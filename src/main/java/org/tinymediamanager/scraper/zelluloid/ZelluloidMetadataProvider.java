/*
 * Copyright 2012 - 2015 Manuel Laggner
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.tinymediamanager.scraper.zelluloid;

import java.io.InputStream;
import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tinymediamanager.scraper.Certification;
import org.tinymediamanager.scraper.MediaCastMember;
import org.tinymediamanager.scraper.MediaGenres;
import org.tinymediamanager.scraper.MediaMetadata;
import org.tinymediamanager.scraper.MediaProviderInfo;
import org.tinymediamanager.scraper.MediaScrapeOptions;
import org.tinymediamanager.scraper.MediaSearchOptions;
import org.tinymediamanager.scraper.MediaSearchResult;
import org.tinymediamanager.scraper.MediaType;
import org.tinymediamanager.scraper.UnsupportedMediaTypeException;
import org.tinymediamanager.scraper.http.CachedUrl;
import org.tinymediamanager.scraper.http.Url;
import org.tinymediamanager.scraper.mediaprovider.IMovieMetadataProvider;
import org.tinymediamanager.scraper.util.MetadataUtil;
import org.tinymediamanager.scraper.util.StrgUtils;

import net.xeoh.plugins.base.annotations.PluginImplementation;

/**
 * The Class ZelluloidMetadataProvider. A meta data provider for the site zelluloid.de
 * 
 * @author Myron Boyle (myron0815@gmx.net)
 */
@PluginImplementation
public class ZelluloidMetadataProvider implements IMovieMetadataProvider { // , IMovieTrailerProvider {
  private static final Logger      LOGGER        = LoggerFactory.getLogger(ZelluloidMetadataProvider.class);
  private static final String      BASE_URL      = "http://www.zelluloid.de";
  private static final String      PAGE_ENCODING = "ISO-8859-1";

  private static MediaProviderInfo providerInfo  = createMediaProviderInfo();

  private static MediaProviderInfo createMediaProviderInfo() {
    MediaProviderInfo providerInfo = new MediaProviderInfo("zelluloid", "zelluloid.de",
        "<html><h3>Zelluloid.de</h3><br />Scraper for the german site zelluloid.de which is able to scrape movie metadata<br /><br />Available languages: german</html>",
        ZelluloidMetadataProvider.class.getResource("/zelluloid_de.png"));

    return providerInfo;
  }

  public ZelluloidMetadataProvider() {
  }

  @Override
  public MediaProviderInfo getProviderInfo() {
    return providerInfo;
  }

  @Override
  public MediaMetadata getMetadata(MediaScrapeOptions options) throws Exception {
    LOGGER.debug("getMetadata() " + options.toString());

    if (options.getType() != MediaType.MOVIE) {
      throw new UnsupportedMediaTypeException(options.getType());
    }

    String id = "";
    if (StringUtils.isNotBlank(options.getId(providerInfo.getId()))) {
      id = options.getId(providerInfo.getId());
    }

    if (StringUtils.isBlank(id) && options.getResult() != null) {
      if (StringUtils.isEmpty(options.getResult().getId())) {
        id = StrgUtils.substr(options.getResult().getUrl(), "id=(.*?)");
      }
      else {
        id = options.getResult().getId();
      }
    }

    // we can not scrape without zelluloid id
    if (StringUtils.isBlank(id)) {
      throw new Exception("cannot scrape without id");
    }

    MediaMetadata md = new MediaMetadata(providerInfo.getId());

    String detailurl = BASE_URL + "/filme/index.php3?id=" + id;

    Url url;
    try {
      url = new CachedUrl(detailurl);
      InputStream in = url.getInputStream();
      Document doc = Jsoup.parse(in, PAGE_ENCODING, "");
      in.close();

      // parse title
      String title = doc.getElementsByAttributeValue("property", "og:title").attr("content").trim();
      md.storeMetadata(MediaMetadata.TITLE, title);

      // parse plot
      String plot = doc.getElementsByAttributeValue("class", "bigtext").text();
      md.storeMetadata(MediaMetadata.PLOT, plot);
      md.storeMetadata(MediaMetadata.TAGLINE, plot.length() > 150 ? plot.substring(0, 150) : plot);

      // parse poster
      Elements el = doc.getElementsByAttributeValueStarting("src", "/images/poster");
      if (el.size() == 1) {
        md.storeMetadata(MediaMetadata.POSTER_URL, BASE_URL + el.get(0).attr("src"));
      }

      // parse year
      el = doc.getElementsByAttributeValueContaining("href", "az.php3?j=");
      if (el.size() == 1) {
        md.storeMetadata(MediaMetadata.YEAR, el.get(0).text());
      }

      // parse cinema release
      el = doc.getElementsByAttributeValueContaining("href", "?v=w");
      if (el.size() > 0) {
        try {
          SimpleDateFormat sdf = new SimpleDateFormat("dd.MM.yyyy");
          Date d = sdf.parse(el.get(0).text());
          md.storeMetadata(MediaMetadata.RELEASE_DATE, d);
        }
        catch (Exception e) {
          LOGGER.warn("cannot parse cinema release date: " + el.get(0).text());
        }
      }

      // parse original title
      md.storeMetadata(MediaMetadata.ORIGINAL_TITLE, StrgUtils.substr(doc.toString(), "Originaltitel: (.*?)\\<"));

      if (StringUtils.isEmpty(md.getStringValue(MediaMetadata.ORIGINAL_TITLE))) {
        md.storeMetadata(MediaMetadata.ORIGINAL_TITLE, md.getStringValue(MediaMetadata.TITLE));
      }

      // parse runtime
      String rt = (StrgUtils.substr(doc.toString(), "ca.&nbsp;(.*?)&nbsp;min"));
      if (!rt.isEmpty()) {
        try {
          md.storeMetadata(MediaMetadata.RUNTIME, Integer.valueOf(rt));
        }
        catch (Exception e2) {
          LOGGER.warn("cannot convert runtime: " + rt);
        }
      }

      // parse genres
      el = doc.getElementsByAttributeValueContaining("href", "az.php3?g=");
      for (Element g : el) {
        String gid = g.attr("href").substring(g.attr("href").lastIndexOf('=') + 1);
        md.addGenre(getTmmGenre(gid));
      }

      // parse cert
      // FSK: ab 12, $230 Mio. Budget
      String fsk = StrgUtils.substr(doc.toString(), "FSK: (.*?)[,<]");
      if (!fsk.isEmpty()) {
        md.addCertification(Certification.findCertification(fsk));
      }

      // parse rating
      Elements ratings = doc.getElementsByAttributeValue("class", "ratingBarTable");
      if (ratings.size() == 2) { // get user rating
        Element e = ratings.get(1);
        // <div>87%</div>
        String r = e.getElementsByTag("div").text().replace("%", "");
        try {
          md.storeMetadata(MediaMetadata.RATING, Double.valueOf(r) / 10); // only 0-10
        }
        catch (Exception e2) {
          LOGGER.warn("cannot convert rating: " + r);
        }
      }

      // details page
      doc = null;
      String detailsUrl = BASE_URL + "/filme/details.php3?id=" + id;
      try {
        url = new CachedUrl(detailsUrl);
        in = url.getInputStream();
        doc = Jsoup.parse(in, PAGE_ENCODING, "");
        in.close();
      }
      catch (Exception e) {
        LOGGER.error("failed to get details: " + e.getMessage());
      }

      if (doc != null) {
        Element tab = doc.getElementById("ccdetails");
        int header = 0;
        String lastRole = "";
        for (Element tr : tab.getElementsByTag("tr")) {
          if (tr.toString().contains("dyngfx")) { // header gfx
            if (tr.toString().contains("Besetzung")) {
              header = 1;
            }
            else if (tr.toString().contains("Crew")) {
              header = 2;
            }
            else if (tr.toString().contains("Produktion")) {
              header = 3;
            }
            else if (tr.toString().contains("Verleih")) {
              header = 4;
            }
            else if (tr.toString().contains("Alternativtitel")) {
              header = 5;
            }
            continue;
          }
          else {
            // no header gfx, so data
            MediaCastMember mcm = new MediaCastMember();
            el = tr.getElementsByTag("td");
            if (header == 1) {
              // actors
              if (el.size() == 2) {
                String role = "" + el.get(0).text().trim();
                // text() decodes &nbsp; to \u00a0
                if (role.equals("\u00a0") || StringUtils.isBlank(role)) {
                  continue;
                }
                mcm.setCharacter(role);
                mcm.setName(el.get(1).getElementsByTag("a").text());
                mcm.setId(StrgUtils.substr(el.get(1).getElementsByTag("a").attr("href"), "id=(\\d+)"));
                mcm.setType(MediaCastMember.CastType.ACTOR);
                md.addCastMember(mcm);
                // parsing actor pages would we too heavy here just for actor images..
              }
            }
            else if (header == 2) {
              // crew
              if (el.size() == 2) {
                String crewrole = el.get(0).html().trim();
                mcm.setName(el.get(1).getElementsByTag("a").text());
                if (crewrole.equals("&nbsp;")) {
                  mcm.setPart(lastRole);
                }
                else {
                  mcm.setPart(crewrole);
                  lastRole = crewrole;
                }
                if (crewrole.equals("Regie")) {
                  mcm.setType(MediaCastMember.CastType.DIRECTOR);
                }
                else if (crewrole.equals("Drehbuch")) {
                  mcm.setType(MediaCastMember.CastType.WRITER);
                }
                else {
                  mcm.setType(MediaCastMember.CastType.OTHER);
                }
                mcm.setId(StrgUtils.substr(el.get(1).getElementsByTag("a").attr("href"), "id=(\\d+)"));
                md.addCastMember(mcm);
              }
            }
            else if (header == 3) {
              // production
              md.storeMetadata(MediaMetadata.PRODUCTION_COMPANY, el.get(0).text());
            }
          }
        }
      }

      // get links page
      doc = null;
      String linksUrl = BASE_URL + "/filme/links.php3?id=" + id;
      try {
        url = new CachedUrl(linksUrl);
        in = url.getInputStream();
        doc = Jsoup.parse(in, PAGE_ENCODING, "");
        in.close();
      }
      catch (Exception e) {
        LOGGER.error("failed to get links page: " + e.getMessage());
      }

      if (doc != null) {
        el = doc.getElementsByAttributeValueContaining("href", "german.imdb.com");
        if (el != null && el.size() > 0) {
          String imdb = StrgUtils.substr(el.get(0).attr("href"), "(tt\\d{7})");
          if (imdb.isEmpty()) {
            imdb = "tt" + StrgUtils.substr(el.get(0).attr("href"), "\\?(\\d+)");
          }
          md.setId(MediaMetadata.IMDB, imdb);
        }
      }
    }
    catch (Exception e) {
      LOGGER.error("Error parsing " + options.getResult().getUrl());

      throw e;
    }

    return md;
  }

  @Override
  public List<MediaSearchResult> search(MediaSearchOptions options) throws Exception {
    LOGGER.debug("search() " + options.toString());

    if (options.getMediaType() != MediaType.MOVIE) {
      throw new UnsupportedMediaTypeException(options.getMediaType());
    }

    List<MediaSearchResult> resultList = new ArrayList<MediaSearchResult>();
    String searchUrl = "";
    String searchTerm = "";
    String imdb = "";

    // only title search
    if (StringUtils.isNotEmpty(options.get(MediaSearchOptions.SearchParam.QUERY))) {
      searchTerm = options.get(MediaSearchOptions.SearchParam.QUERY);
      searchUrl = BASE_URL + "/suche/index.php3?qstring=" + URLEncoder.encode(searchTerm, "UTF-8");
      LOGGER.debug("search for : " + searchTerm);
    }
    else {
      LOGGER.debug("empty searchString");
      return resultList;
    }

    searchTerm = MetadataUtil.removeNonSearchCharacters(searchTerm);

    Document doc = null;
    try {
      Url url = new CachedUrl(searchUrl);
      InputStream in = url.getInputStream();
      doc = Jsoup.parse(in, PAGE_ENCODING, "");
      in.close();
    }
    catch (Exception e) {
      LOGGER.error("failed to search for " + searchTerm + ": " + e.getMessage());
    }

    if (doc == null) {
      return resultList;
    }

    // only look for movie links
    // Elements filme = doc.getElementsByAttributeValueStarting("href", "hit.php");

    // <TR><TD ALIGN=CENTER><IMG SRC="/gfx/icoMovie.gif" WIDTH=26 HEIGHT=26 ALT="Film"></TD><TD><B><a
    // href="hit.php3?hit=3700de0676109950820a042115e98d99-movie-886-23126993-2" class="normLight">Twelve
    // Monkeys</B> <nobr>(1995)</nobr></a><div class="smallBlur">R: Terry Gilliam</div></TD>
    Elements filme = doc.getElementsByTag("tr");
    for (Element tr : filme) {
      // no nesting trs
      if (tr.getElementsByTag("tr").size() > 1) {
        continue;
      }
      // only tr with movie links
      Elements as = tr.getElementsByAttributeValueStarting("href", "hit.php3?hit=");
      if (as.isEmpty()) {
        continue;
      }
      // and only movies
      if (tr.text().contains("TV-Serie")) {
        continue;
      }
      try {
        Element a = as.first();
        String id = StrgUtils.substr(a.attr("href"), "-movie-(.*?)-");
        MediaSearchResult sr = new MediaSearchResult(providerInfo.getId());
        sr.setId(id);

        if (StringUtils.isEmpty(sr.getTitle())) {
          if (a.html().contains("nobr")) {
            sr.setTitle(a.ownText());
          }
          else {
            sr.setTitle(a.text());
          }
        }
        LOGGER.debug("found movie " + sr.getTitle());
        sr.setOriginalTitle(a.getElementsByTag("span").text());

        sr.setYear(StrgUtils.substr(tr.getElementsByTag("nobr").text(), ".*(\\d{4}).*")); // any 4 digit
        sr.setMediaType(MediaType.MOVIE);
        sr.setUrl(BASE_URL + "/filme/index.php3?id=" + id);
        // sr.setPosterUrl(BASE_URL + "/images" + StrgUtils.substr(a.toString(),
        // "images(.*?)\\&quot"));

        if (imdb.equals(sr.getIMDBId())) {
          // perfect match
          sr.setScore(1);
        }
        else {
          // compare score based on names
          sr.setScore(MetadataUtil.calculateScore(searchTerm, sr.getTitle()));
        }

        resultList.add(sr);
      }
      catch (Exception e) {
        LOGGER.warn("error parsing movie result: " + e.getMessage());
      }

    }
    LOGGER.debug("found " + resultList.size() + " search results");

    // didn't we find anything? we may have been redirected to the details page
    if (resultList.isEmpty()) {
      if (!doc.getElementsByTag("title").text().contains("Suche nach")) {
        // redirected to detail page
        MediaSearchResult msr = new MediaSearchResult(providerInfo.getId());
        Elements el = doc.getElementsByAttributeValueStarting("href", "index.php3?id=");
        if (el.size() > 0) {
          msr.setId(StrgUtils.substr(el.get(0).attr("href"), "id=(\\d+)"));
        }
        msr.setTitle(StrgUtils.substr(doc.getElementsByTag("title").text(), "(.*?)\\|").trim());
        el = doc.getElementsByAttributeValueContaining("href", "az.php3?j=");
        if (el.size() == 1) {
          msr.setYear(el.get(0).text());
        }
        resultList.add(msr);
      }
      return resultList;
    }

    Collections.sort(resultList);
    Collections.reverse(resultList);
    return resultList;
  }

  // @Override
  // public List<MediaTrailer> getTrailers(MediaScrapeOptions options) throws Exception {
  // // http://www.zelluloid.de/filme/trailer.php3?id=7614
  // return new ArrayList<MediaTrailer>(0);
  // }

  private MediaGenres getTmmGenre(String genre) {
    MediaGenres g = null;
    if (genre.isEmpty()) {
      return g;
    }
    try {
      int gid = Integer.parseInt(genre);
      // @formatter:off
      switch (gid) {
        case 2:   g = MediaGenres.COMEDY; break; // Komödie
        case 3:   g = MediaGenres.ACTION; break; // Action
        case 4:   g = MediaGenres.THRILLER; break; // Thriller
        case 5:   g = MediaGenres.WAR; break; // Krieg
        case 6:   g = MediaGenres.SCIENCE_FICTION; break; // Science-Fiction
        case 7:   g = MediaGenres.FANTASY; break; // Fantasy
        case 9:   g = MediaGenres.ANIMATION; break; // Zeichentrick
        case 10:  g = MediaGenres.ANIMATION; break; // Computeranim...
        case 11:  g = null; break; // Remake
        case 13:  g = MediaGenres.ANIMATION; break; // Anime
        case 14:  g = MediaGenres.DRAMA; break; // Drama
        case 15:  g = MediaGenres.DOCUMENTARY; break; // Dokumentation
        case 16:  g = MediaGenres.ADVENTURE; break; // Abenteuer
        case 17:  g = MediaGenres.ROMANCE; break; // Lovestory
        case 18:  g = MediaGenres.ANIMATION; break; // Comicverfilmung
        case 19:  g = MediaGenres.ROAD_MOVIE; break; // Roadmovie
        case 22:  g = MediaGenres.HORROR; break; // Horror
        case 23:  g = MediaGenres.EROTIC; break; // Erotik
        case 25:  g = MediaGenres.DISASTER; break; // Katastrophe
        case 26:  g = MediaGenres.THRILLER; break; // Spionage
        case 27:  g = MediaGenres.SPORT; break; // Kampfsport
        case 28:  g = MediaGenres.BIOGRAPHY; break; // Biografie
        case 29:  g = MediaGenres.HISTORY; break; // Ritter
        case 30:  g = MediaGenres.SCIENCE_FICTION; break; // Endzeit
        case 31:  g = MediaGenres.SCIENCE_FICTION; break; // Cyberspace
        case 32:  g = MediaGenres.SCIENCE_FICTION; break; // Computer
        case 33:  g = MediaGenres.WESTERN; break; // Western
        case 34:  g = MediaGenres.CRIME; break; // Gericht
        case 35:  g = MediaGenres.WAR; break; // U-Boot
        case 36:  g = MediaGenres.CRIME; break; // Krimi
        case 37:  g = MediaGenres.HORROR; break; // Splatter
        case 38:  g = MediaGenres.MUSICAL; break; // Musical
        case 39:  g = MediaGenres.MUSIC; break; // Musik
        case 40:  g = MediaGenres.FAMILY; break; // Familie
        case 42:  g = MediaGenres.MYSTERY; break; // Mystery
        case 43:  g = MediaGenres.SPORT; break; // Sport
        case 44:  g = MediaGenres.REALITY_TV; break; // Schule
        case 45:  g = MediaGenres.WAR; break; // Militär
        case 46:  g = MediaGenres.ANIMATION; break; // Trick
        case 47:  g = MediaGenres.INDIE; break; // Experimental...
        case 48:  g = MediaGenres.HORROR; break; // Vampire
        case 49:  g = MediaGenres.SCIENCE_FICTION; break; // Zeitreise
        case 50:  g = MediaGenres.FANTASY; break; // Märchen
        case 51:  g = MediaGenres.CRIME; break; // Serienkiller
        case 52:  g = MediaGenres.SILENT_MOVIE; break; // Stummfilm
        case 53:  g = MediaGenres.SHORT; break; // Kurzfilm
        case 54:  g = MediaGenres.INDIE; break; // Blaxploitation
        case 55:  g = MediaGenres.FAMILY; break; // Heimat
        case 56:  g = MediaGenres.SCIENCE_FICTION; break; // Spielverfilmung
        case 59:  g = MediaGenres.FAMILY; break; // Weihnachten
        case 61:  g = MediaGenres.SERIES; break; // Soap
        case 62:  g = MediaGenres.HISTORY; break; // Piraten
        case 63:  g = MediaGenres.FOREIGN; break; // Bollywood
        case 64:  g = MediaGenres.GAME_SHOW; break; // Show
        case 65:  g = null; break; // 3D
        case 68:  g = MediaGenres.HORROR; break; // Zombies
      }
      // @formatter:on
    }
    catch (Exception e) {
      g = MediaGenres.getGenre(genre);
    }
    return g;
  }
}
