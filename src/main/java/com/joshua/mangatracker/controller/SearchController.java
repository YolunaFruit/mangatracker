package com.joshua.mangatracker.controller;

import com.joshua.mangatracker.dto.MangaSearchResult;
import com.joshua.mangatracker.service.MangaSearchAggregatorService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/search")
public class SearchController {

    private final MangaSearchAggregatorService search;

    public SearchController(MangaSearchAggregatorService search) {
        this.search = search;
    }

    @GetMapping("/home")
    public HomeResponse home() {
        // Keep your existing home structure but now through aggregator->MangaDex browse
        List<MangaSearchResult> trending = search.browse("trending", 12);
        List<MangaSearchResult> newest = search.browse("latest", 12);
        return new HomeResponse(trending, newest);
    }

    @GetMapping("/list")
    public List<MangaSearchResult> list(
            @RequestParam String title,
            @RequestParam(defaultValue = "25") int limit
    ) {
        return search.searchListMerged(title, limit);
    }

    @GetMapping("/details")
    public MangaSearchResult details(@RequestParam String id) {
        MangaSearchResult result = search.detailsByPackedId(id);
        if (result == null) throw new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE, "Manga details are temporarily unavailable.");
        return result;
    }

    @GetMapping("/tags")
    public Object tags() {
        return search.tagCatalog();
    }

    @GetMapping("/advanced")
    public List<MangaSearchResult> advanced(
            @RequestParam(required = false) String title,
            @RequestParam(required = false) List<String> includeTags,
            @RequestParam(required = false) List<String> excludeTags,
            @RequestParam(defaultValue = "relevance") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir,
            @RequestParam(defaultValue = "25") int limit
    ) {
        return search.advancedSearch(title, includeTags, excludeTags, sortBy, sortDir, limit);
    }

    @GetMapping("/random")
    public MangaSearchResult random(
            @RequestParam(required = false) String title,
            @RequestParam(required = false) List<String> includeTags,
            @RequestParam(required = false) List<String> excludeTags,
            @RequestParam(defaultValue = "relevance") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir
    ) {
        return search.randomWithFilters(title, includeTags, excludeTags, sortBy, sortDir);
    }

    @GetMapping("/chapters")
    public java.util.Map<String, Object> chapters(@RequestParam String id) {
        java.util.Map<String, Object> response = new java.util.LinkedHashMap<>();
        response.put("latestChapter", search.latestChapter(id));
        return response;
    }

    public record HomeResponse(List<MangaSearchResult> trending,
                               @com.fasterxml.jackson.annotation.JsonProperty("new") List<MangaSearchResult> newList) {}
}
