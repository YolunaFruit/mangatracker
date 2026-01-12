package com.joshua.mangatracker.service.providers;

import com.joshua.mangatracker.dto.MangaSearchResult;

import java.util.List;
import java.util.Map;

public interface MangaProvider {

    /**
     * Short stable key used in packed IDs: "md", "mal", etc.
     */
    String key();

    /**
     * Search provider by query.
     * Return ids as packed IDs (ex: "md:<uuid>" or "mal:123").
     */
    List<MangaSearchResult> search(String query, int limit);

    /**
     * Fetch details by raw provider id (not packed).
     * This method should return a DTO whose id is packed again.
     */
    MangaSearchResult details(String rawId);

    /**
     * Optional: provider supports advanced search filters (like MangaDex tags).
     */
    default boolean supportsAdvanced() { return false; }

    default List<MangaSearchResult> advancedSearch(
            String title,
            List<String> includeTags,
            List<String> excludeTags,
            String sortBy,
            String sortDir,
            int limit
    ) {
        throw new UnsupportedOperationException("Advanced search not supported by: " + key());
    }

    /**
     * Optional: provider supports browse.
     */
    default boolean supportsBrowse() { return false; }

    default List<MangaSearchResult> browse(String type, int limit) {
        throw new UnsupportedOperationException("Browse not supported by: " + key());
    }

    /**
     * Optional: provider supports tag catalog.
     */
    default Map<String, ?> tagCatalog() {
        throw new UnsupportedOperationException("Tags not supported by: " + key());
    }
}
