package com.joshua.mangatracker.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.joshua.mangatracker.dto.MangaSearchResult;
import com.joshua.mangatracker.service.providers.MangaProvider;
import com.joshua.mangatracker.service.providers.ProviderId;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class MangaDexServiceTests {
    @Test void latestChapterIsTheHighestNumberNotTheNumberOfAvailableTranslations() throws Exception {
        var volumes = new ObjectMapper().readTree("""
            {"1":{"chapters":{"1":{"chapter":"1"},"20":{"chapter":"20"},"none":{"chapter":"none"}}},
             "2":{"chapters":{"100":{"chapter":"100"},"101":{"chapter":"100.5"}}}}
            """);
        assertThat(MangaDexService.latestNumberedChapter(volumes)).isEqualTo(101);
    }

    @Test void randomWithoutATitleUsesBrowseInsteadOfAnEmptySearch() {
        MangaProvider md = mock(MangaProvider.class);
        when(md.key()).thenReturn("md");
        MangaSearchResult result = new MangaSearchResult();
        result.setTitle("A random read");
        when(md.browse("trending", 50)).thenReturn(List.of(result));
        var aggregator = new MangaSearchAggregatorService(List.of(md));
        assertThat(aggregator.randomWithFilters("", List.of(), List.of(), "relevance", "desc")).isSameAs(result);
        verify(md, never()).search(anyString(), anyInt());
    }

    @Test void acceptsBothLegacyAndPackedMangaDexIdsButRejectsOtherPaths() {
        String raw = "a1c7c817-4e59-43b7-9365-09675a149a6f";
        assertThat(ProviderId.parseCompatible(raw)).isEqualTo(ProviderId.parseCompatible("md:" + raw));
        assertThat(ProviderId.parseCompatible("mal:123").provider).isEqualTo("mal");
        assertThatThrownBy(() -> ProviderId.parseCompatible("md:../../author")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ProviderId.parseCompatible("unknown:123")).isInstanceOf(IllegalArgumentException.class);
    }
}
