/*
 * Copyright 2012 - 2018 Manuel Laggner
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import java.util.List;

import org.junit.Assert;
import org.junit.Test;
import org.tinymediamanager.scraper.MediaMetadata;
import org.tinymediamanager.scraper.MediaScrapeOptions;
import org.tinymediamanager.scraper.MediaSearchOptions;
import org.tinymediamanager.scraper.MediaSearchResult;
import org.tinymediamanager.scraper.entities.Certification;
import org.tinymediamanager.scraper.entities.MediaCastMember;
import org.tinymediamanager.scraper.entities.MediaCastMember.CastType;
import org.tinymediamanager.scraper.entities.MediaGenres;
import org.tinymediamanager.scraper.entities.MediaRating;
import org.tinymediamanager.scraper.entities.MediaType;

public class ZelluloidMetadataProviderTest {

  @Test
  public void testSearch() {
    ZelluloidMetadataProvider mp;

    // Die Bourne Identität - 2002 - ID 1957
    try {
      mp = new ZelluloidMetadataProvider();
      MediaSearchOptions op = new MediaSearchOptions(MediaType.MOVIE, "Die Bourne Identität");
      List<MediaSearchResult> results = mp.search(op);

      assertThat(results).isNotNull().isNotEmpty();
      assertThat(results.size()).isGreaterThanOrEqualTo(2);
      assertThat(results.get(0).getId()).isEqualTo("1957");
      assertThat(results.get(0).getTitle()).isEqualTo("Die Bourne Identität");
      assertThat(results.get(0).getYear()).isEqualTo(2002);
    }
    catch (Exception e) {
      e.printStackTrace();
      fail(e.getMessage());
    }

    // 12 Monkeys - 1995 - ID 886
    try {
      mp = new ZelluloidMetadataProvider();
      MediaSearchOptions op = new MediaSearchOptions(MediaType.MOVIE, "12 Monkeys");
      List<MediaSearchResult> results = mp.search(op);

      assertThat(results).isNotNull().isNotEmpty();
      assertThat(results.size()).isGreaterThanOrEqualTo(1);
      assertThat(results.get(0).getId()).isEqualTo("886");
      assertThat(results.get(0).getTitle()).isEqualTo("Twelve Monkeys");
      assertThat(results.get(0).getYear()).isEqualTo(1995);
    }
    catch (Exception e) {
      e.printStackTrace();
      fail(e.getMessage());
    }

    // redirect
    // V wie Vendetta - 2005 - ID 5656
    try {
      mp = new ZelluloidMetadataProvider();
      MediaSearchOptions op = new MediaSearchOptions(MediaType.MOVIE, "V wie Vendetta");
      List<MediaSearchResult> results = mp.search(op);

      assertThat(results).isNotNull().isNotEmpty();
      assertThat(results.size()).isGreaterThanOrEqualTo(1);
      assertThat(results.get(0).getId()).isEqualTo("5656");
      assertThat(results.get(0).getTitle()).isEqualTo("V wie Vendetta");
      assertThat(results.get(0).getYear()).isEqualTo(2005);
    }
    catch (Exception e) {
      e.printStackTrace();
      fail(e.getMessage());
    }
  }

  @Test
  public void testScrape() {
    ZelluloidMetadataProvider mp;

    try {
      mp = new ZelluloidMetadataProvider();

      MediaScrapeOptions scop = new MediaScrapeOptions(MediaType.MOVIE);
      scop.setId(mp.getProviderInfo().getId(), "886");
      MediaMetadata md = mp.getMetadata(scop);

      assertThat(md).isNotNull();
      assertThat(md.getTitle()).isEqualTo("Twelve Monkeys");
      assertThat(md.getYear()).isEqualTo(1995);
      assertThat(md.getPlot()).startsWith("Terry Gilliam, einer der Väter der britischen");
      assertThat(md.getOriginalTitle()).isEqualTo("Twelve Monkeys");
      assertThat(md.getRuntime()).isEqualTo(130);
      assertThat(md.getProductionCompanies()).containsOnly("Universal");
      assertThat(md.getGenres()).isNotNull().isNotEmpty();
      assertThat(md.getGenres()).contains(MediaGenres.THRILLER, MediaGenres.SCIENCE_FICTION);
      assertThat(md.getCertifications()).isNotNull().isNotEmpty();
      assertThat(md.getCertifications()).contains(Certification.DE_FSK16);

      assertThat(md.getRatings().size()).isEqualTo(1);
      MediaRating mediaRating = md.getRatings().get(0);
      assertThat(mediaRating.getRating()).isGreaterThan(0);
      assertThat(mediaRating.getMaxValue()).isEqualTo(100);

      assertThat(md.getCastMembers(CastType.ACTOR)).isNotNull().isNotEmpty();
      assertThat(md.getCastMembers(CastType.ACTOR).size()).isEqualTo(22);

      MediaCastMember cm = md.getCastMembers(CastType.ACTOR).get(0);
      assertThat(cm.getName()).isEqualTo("Bruce Willis");
      assertThat(cm.getCharacter()).isEqualTo("James Cole");

      assertThat(md.getCastMembers(CastType.DIRECTOR)).isNotNull().isNotEmpty();
      assertThat(md.getCastMembers(CastType.DIRECTOR).size()).isEqualTo(1);
      cm = md.getCastMembers(CastType.DIRECTOR).get(0);
      assertThat(cm.getName()).isEqualTo("Terry Gilliam");

    }
    catch (Exception e) {
      e.printStackTrace();
      Assert.fail();
    }
  }

}
