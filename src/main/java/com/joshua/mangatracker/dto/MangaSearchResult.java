package com.joshua.mangatracker.dto;

import java.util.ArrayList;
import java.util.List;

public class MangaSearchResult {
    private String id;             // packed: "md:..." or "mal:..."
    private String title;
    private String description;
    private String coverUrl;
    private int chapterCount;
    private List<String> tags = new ArrayList<>();
    private List<String> altTitles = new ArrayList<>();

    private String author;         // display string
    private String source;         // "MangaDex" / "MyAnimeList"
    private String sourceUrl;      // optional: link to MAL/MD details page

    public MangaSearchResult() {}

    public MangaSearchResult(String id, String title, String description, String coverUrl, int chapterCount, List<String> tags) {
        this.id = id;
        this.title = title;
        this.description = description;
        this.coverUrl = coverUrl;
        this.chapterCount = chapterCount;
        this.tags = (tags == null) ? new ArrayList<>() : tags;
    }

    public MangaSearchResult(String id, String title, String description, String coverUrl, int chapterCount, List<String> tags, List<String> altTitles) {
        this(id, title, description, coverUrl, chapterCount, tags);
        this.altTitles = (altTitles == null) ? new ArrayList<>() : altTitles;
    }

    // setter/get
    public MangaSearchResult withAuthor(String author) { this.author = author; return this; }
    public MangaSearchResult withSource(String source) { this.source = source; return this; }
    public MangaSearchResult withSourceUrl(String sourceUrl) { this.sourceUrl = sourceUrl; return this; }

    // getters/setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getCoverUrl() { return coverUrl; }
    public void setCoverUrl(String coverUrl) { this.coverUrl = coverUrl; }

    public int getChapterCount() { return chapterCount; }
    public void setChapterCount(int chapterCount) { this.chapterCount = chapterCount; }

    public List<String> getTags() { return tags; }
    public void setTags(List<String> tags) { this.tags = (tags == null) ? new ArrayList<>() : tags; }

    public List<String> getAltTitles() { return altTitles; }
    public void setAltTitles(List<String> altTitles) { this.altTitles = (altTitles == null) ? new ArrayList<>() : altTitles; }

    public String getAuthor() { return author; }
    public void setAuthor(String author) { this.author = author; }

    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }

    public String getSourceUrl() { return sourceUrl; }
    public void setSourceUrl(String sourceUrl) { this.sourceUrl = sourceUrl; }
}
