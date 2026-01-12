package com.joshua.mangatracker.service;

import com.joshua.mangatracker.dto.MangaSearchResult;
import com.joshua.mangatracker.service.providers.MangaProvider;
import com.joshua.mangatracker.service.providers.ProviderId;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class MangaSearchAggregatorService {

    private final Map<String, MangaProvider> providersByKey;

    public MangaSearchAggregatorService(List<MangaProvider> providers) {
        Map<String, MangaProvider> map = new HashMap<>();
        for (MangaProvider p : providers) {
            map.put(p.key(), p);
        }
        this.providersByKey = Collections.unmodifiableMap(map);
    }

    private MangaProvider pOrNull(String key) {
        return providersByKey.get(key);
    }

    private MangaProvider pOrThrow(String key) {
        MangaProvider p = providersByKey.get(key);
        if (p == null) throw new IllegalStateException("Missing provider bean: " + key);
        return p;
    }

    public List<MangaSearchResult> searchListMerged(String query, int limit) {
        limit = clamp(limit, 1, 50);

        MangaProvider mal = pOrNull("mal");
        MangaProvider md  = pOrNull("md");

        List<MangaSearchResult> malResults = (mal == null) ? List.of() : mal.search(query, limit);
        List<MangaSearchResult> mdResults  = (md  == null) ? List.of() : md.search(query, limit);

        // MAL wins during dedupe
        LinkedHashMap<String, MangaSearchResult> byTitle = new LinkedHashMap<>();
        for (MangaSearchResult r : malResults) byTitle.put(normTitle(r.getTitle()), r);
        for (MangaSearchResult r : mdResults) byTitle.putIfAbsent(normTitle(r.getTitle()), r);

        return byTitle.values().stream().limit(limit).collect(Collectors.toList());
    }

    public MangaSearchResult detailsByPackedId(String packedId) {
        ProviderId pid = ProviderId.parsePacked(packedId);
        MangaProvider provider = pOrThrow(pid.provider);
        return provider.details(pid.rawId);
    }

    // MangaDex-only advanced (for now), might remove later
    public List<MangaSearchResult> advancedSearch(
            String title,
            List<String> includeTags,
            List<String> excludeTags,
            String sortBy,
            String sortDir,
            int limit
    ) {
        MangaProvider md = pOrThrow("md");
        return md.advancedSearch(title, includeTags, excludeTags, sortBy, sortDir, limit);
    }

    public Object tagCatalog() {
        MangaProvider md = pOrThrow("md");
        return md.tagCatalog();
    }

    public List<MangaSearchResult> browse(String type, int limit) {
        MangaProvider md = pOrThrow("md");
        return md.browse(type, limit);
    }

    public MangaSearchResult randomWithFilters(
            String title,
            List<String> includeTags,
            List<String> excludeTags,
            String sortBy,
            String sortDir
    ) {
        MangaProvider md = pOrNull("md");

        boolean hasFilters =
                (includeTags != null && !includeTags.isEmpty()) ||
                        (excludeTags != null && !excludeTags.isEmpty());

        List<MangaSearchResult> pool;
        if (hasFilters && md != null) {
            pool = md.advancedSearch(title, includeTags, excludeTags, sortBy, sortDir, 50);
        } else {
            pool = searchListMerged(title == null ? "" : title, 50);
        }

        if (pool == null || pool.isEmpty()) return null;
        return pool.get(new Random().nextInt(pool.size()));
    }

    private int clamp(int v, int lo, int hi) { return Math.max(lo, Math.min(hi, v)); }

    private String normTitle(String t) {
        if (t == null) return "";
        return t.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }
}
