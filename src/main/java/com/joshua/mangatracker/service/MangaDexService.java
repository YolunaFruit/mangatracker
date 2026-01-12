package com.joshua.mangatracker.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.joshua.mangatracker.dto.MangaSearchResult;
import com.joshua.mangatracker.service.providers.MangaProvider;
import com.joshua.mangatracker.service.providers.ProviderId;
import org.springframework.stereotype.Service;
import com.joshua.mangatracker.service.providers.MangaProvider;
import com.joshua.mangatracker.service.providers.ProviderId;


import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class MangaDexService implements MangaProvider {

    private static final String BASE_URL = "https://api.mangadex.org";

    // langs
    private static final Set<String> ALLOWED_ORIGINAL_LANGUAGES = Set.of("ja", "ko", "zh", "zh-hk");

    // Performance: caches
    private final Map<String, Optional<String>> coverUrlCache = new ConcurrentHashMap<>();
    private final Map<String, Integer> chapterCountCache = new ConcurrentHashMap<>();
    private final Map<String, Optional<String>> authorNameCache = new ConcurrentHashMap<>();

    // grouped catalog (lazy init)
    private volatile Map<String, List<TagOption>> tagCatalogCache = null;

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;



    private static final String RATING_PREFIX = "rating:"; // special ids: rating:suggestive, rating:erotica

    private static final Set<String> UI_TAGS = Set.of(
            "Action", "Adventure", "Comedy", "Drama", "Fantasy", "Sci-Fi",
            "Romance", "Slice of Life", "Sports", "Thriller", "Horror",
            "Mystery", "Psychological", "Supernatural",
            "Boys Love", "Girls Love"
    );

    private static final Set<String> UI_THEMES = Set.of(
            "School Life", "Isekai", "Time Travel", "Reincarnation",
            "Gender Bender", "Martial Arts", "Mecha", "Music", "Cooking",
            "Historical", "Military", "Detective", "Survival"
    );

    // content warnings & hard blocks

    private static final Set<String> UI_CONTENT_WARNINGS = Set.of(
            "Gore", "Violence", "Sexual Violence", "Abuse", "Suicide", "Self-Harm"
    );

    private static final Set<String> HARD_BLOCK = Set.of(
            "Incest", "Lolicon", "Shotacon", "Shota", "Loli", "Monster Girls", "Monster"
    );

    private static final Set<String> HARD_BLOCK_NORM;
    static {
        Set<String> tmp = new HashSet<>();
        for (String s : HARD_BLOCK) {
            if (s != null) tmp.add(s.trim().toLowerCase(Locale.ROOT));
        }
        HARD_BLOCK_NORM = Collections.unmodifiableSet(tmp);
    }

    private static boolean isHardBlockedTagName(String name) {
        if (name == null) return false;
        return HARD_BLOCK_NORM.contains(name.trim().toLowerCase(Locale.ROOT));
    }

    public MangaDexService() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(8))
                .build();
        this.objectMapper = new ObjectMapper();
    }

    // MangaProvider
    @Override public String key() { return "md"; }
    public String displayName() { return "MangaDex"; }



    // public DTO & search
    public static class TagOption {
        public String id;
        public String name;
        public String group;

        public TagOption() {}
        public TagOption(String id, String name, String group) {
            this.id = id;
            this.name = name;
            this.group = group;
        }
    }

    // basic search
    @Override
    public List<MangaSearchResult> search(String query, int limit) {
        // For MangaDex, query is title search (fast) + author-name search (best-effort)
        limit = clamp(limit, 1, 50);
        if (query == null || query.isBlank()) return List.of();

        // 1) title-based
        List<MangaSearchResult> titleResults = searchMultiple(query, limit);

        // 2) author-name based (best-effort); merge (no duplicates by packed id)
        List<MangaSearchResult> authorResults = searchByAuthorName(query, Math.min(15, limit));

        LinkedHashMap<String, MangaSearchResult> merged = new LinkedHashMap<>();
        for (MangaSearchResult r : titleResults) merged.put(r.getId(), r);
        for (MangaSearchResult r : authorResults) merged.putIfAbsent(r.getId(), r);

        return merged.values().stream().limit(limit).toList();
    }

    // details & descriptions
    @Override
    public MangaSearchResult details(String providerRawId) {
        return fetchMangaDetails(providerRawId);
    }

    // sort by list
    @Override
    public List<MangaSearchResult> browse(String type, int limit) {
        String t = (type == null) ? "trending" : type.trim().toLowerCase();

        String orderQuery = switch (t) {
            case "latest", "updated", "new" -> "order[latestUploadedChapter]=desc";
            case "rating", "highest", "highestrating" -> "order[rating]=desc";
            case "trending", "popular", "follows" -> "order[followedCount]=desc";
            default -> "order[followedCount]=desc";
        };

        return browseMangaList(orderQuery, limit);
    }

    // fast search
    public List<MangaSearchResult> searchMultiple(String title, int limit) {
        limit = clamp(limit, 1, 50);

        List<MangaSearchResult> out = new ArrayList<>();
        try {
            List<JsonNode> items = queryMangaByTitle(title, limit);

            Map<String, String> mangaIdToCoverId = new HashMap<>();
            Set<String> authorIds = new HashSet<>();
            Map<String, String> mangaIdToAuthorId = new HashMap<>();

            for (JsonNode item : items) {
                String mangaId = item.path("id").asText("");
                if (mangaId.isBlank()) continue;

                JsonNode attrs = item.path("attributes");
                if (!passesCoreFilters(attrs)) continue;

                String displayTitle = buildDisplayTitle(attrs);
                if (displayTitle.isBlank()) continue;

                String coverId = findCoverId(item.path("relationships"));
                if (coverId != null) mangaIdToCoverId.put(mangaId, coverId);
                else coverUrlCache.putIfAbsent(mangaId, Optional.empty());

                String authorId = findAuthorId(item.path("relationships"));
                if (authorId != null) {
                    authorIds.add(authorId);
                    mangaIdToAuthorId.put(mangaId, authorId);
                }
            }

            Map<String, String> coverIdToFileName =
                    batchFetchCoverFileNames(new ArrayList<>(new HashSet<>(mangaIdToCoverId.values())));

            // Resolve authors (cached)
            Map<String, String> authorIdToName = new HashMap<>();
            for (String aid : authorIds) {
                authorIdToName.put(aid, getOrFetchAuthorName(aid));
            }

            for (JsonNode item : items) {
                String mangaId = item.path("id").asText("");
                if (mangaId.isBlank()) continue;

                JsonNode attrs = item.path("attributes");
                if (!passesCoreFilters(attrs)) continue;

                String displayTitle = buildDisplayTitle(attrs);
                if (displayTitle.isBlank()) continue;

                String desc = normalizeDescription(attrs.path("description").path("en").asText(""));
                List<String> tags = extractEnglishTags(attrs);

                String coverUrl = getOrCacheCoverUrl(mangaId, mangaIdToCoverId.get(mangaId), coverIdToFileName);

                String author = "";
                String authorId = mangaIdToAuthorId.get(mangaId);
                if (authorId != null) author = authorIdToName.getOrDefault(authorId, "");

                String packedId = ProviderId.of(key(), mangaId).packed();

                out.add(new MangaSearchResult(packedId, displayTitle, desc, coverUrl, 0, tags)
                        .withAuthor(author)
                        .withSource(displayName())
                        .withSourceUrl("https://mangadex.org/title/" + mangaId));
            }

            return out;

        } catch (Exception e) {
            return out;
        }
    }

    // details
    public MangaSearchResult fetchMangaDetails(String mangaId) {
        try {
            JsonNode data = getJson(BASE_URL + "/manga/" + mangaId).path("data");
            JsonNode attrs = data.path("attributes");

            String title = buildDisplayTitle(attrs);
            String desc = normalizeDescription(attrs.path("description").path("en").asText(""));

            List<String> tags = extractEnglishTags(attrs);
            List<String> altTitles = collectAltTitles(attrs);

            // cover
            String coverUrl;
            Optional<String> cached = coverUrlCache.get(mangaId);
            if (cached != null) {
                coverUrl = cached.orElse(null);
            } else {
                String coverId = findCoverId(data.path("relationships"));
                String fileName = (coverId == null) ? "" : fetchCoverFileName(coverId);
                coverUrl = (fileName == null || fileName.isBlank()) ? null : buildCoverUrl(mangaId, fileName);
                coverUrlCache.put(mangaId, Optional.ofNullable(coverUrl));
            }

            int chapterCount = fetchTotalChapters(mangaId);

            // author
            String author = "";
            String authorId = findAuthorId(data.path("relationships"));
            if (authorId != null) author = getOrFetchAuthorName(authorId);

            String packedId = ProviderId.of(key(), mangaId).packed();

            MangaSearchResult out = new MangaSearchResult(packedId, title, desc, coverUrl, chapterCount, tags, altTitles);
            out.setAuthor(author);
            out.setSource(displayName());
            out.setSourceUrl("https://mangadex.org/title/" + mangaId);

            return out;

        } catch (Exception e) {
            return null;
        }
    }

    // search & tags
    @Override
    public Map<String, List<TagOption>> tagCatalog() {
        Map<String, List<TagOption>> cached = tagCatalogCache;
        if (cached != null) return cached;

        synchronized (this) {
            if (tagCatalogCache != null) return tagCatalogCache;

            Map<String, List<TagOption>> grouped = new LinkedHashMap<>();
            grouped.put("Tags", new ArrayList<>());
            grouped.put("Themes", new ArrayList<>());
            grouped.put("Content Warnings", new ArrayList<>());

            try {
                JsonNode root = getJson(BASE_URL + "/manga/tag");
                JsonNode data = root.path("data");
                if (!data.isArray()) {
                    tagCatalogCache = grouped;
                    return grouped;
                }

                String ecchiId = null;
                for (JsonNode tagNode : data) {
                    String id = tagNode.path("id").asText("");
                    JsonNode attrs = tagNode.path("attributes");

                    String name = attrs.path("name").path("en").asText("");
                    String group = attrs.path("group").asText("");
                    if (id.isBlank() || name.isBlank() || group.isBlank()) continue;

                    if ("Ecchi".equalsIgnoreCase(name)) {
                        ecchiId = id;
                    }

                    if (isHardBlockedTagName(name)) continue;

                    boolean allowed =
                            (group.equals("genre") && UI_TAGS.contains(name)) ||
                                    (group.equals("theme") && UI_THEMES.contains(name)) ||
                                    (group.equals("content") && UI_CONTENT_WARNINGS.contains(name));

                    if (!allowed) continue;

                    // keep Ecchi out of normal lists (we inject it under warnings)
                    if ("Ecchi".equalsIgnoreCase(name)) continue;

                    TagOption opt = new TagOption(id, name, group);
                    switch (group) {
                        case "genre" -> grouped.get("Tags").add(opt);
                        case "theme" -> grouped.get("Themes").add(opt);
                        case "content" -> grouped.get("Content Warnings").add(opt);
                    }
                }

                grouped.get("Tags").sort(Comparator.comparing(o -> o.name.toLowerCase()));
                grouped.get("Themes").sort(Comparator.comparing(o -> o.name.toLowerCase()));

                List<TagOption> cw = grouped.get("Content Warnings");

                // Inject: Suggestive, Ecchi, Erotica
                List<TagOption> specials = new ArrayList<>();
                specials.add(new TagOption(RATING_PREFIX + "suggestive", "Suggestive", "rating"));
                if (ecchiId != null) specials.add(new TagOption(ecchiId, "Ecchi", "genre"));
                specials.add(new TagOption(RATING_PREFIX + "erotica", "Erotica", "rating"));

                cw.addAll(0, specials);

                int specialsCount = specials.size();
                if (cw.size() > specialsCount) {
                    cw.subList(specialsCount, cw.size())
                            .sort(Comparator.comparing(o -> o.name.toLowerCase()));
                }

            } catch (Exception ignored) {}

            tagCatalogCache = grouped;
            return grouped;
        }
    }

    // mangadex search
    @Override
    public List<MangaSearchResult> advancedSearch(
            String title,
            List<String> includeTags,
            List<String> excludeTags,
            String sortBy,
            String sortDir,
            int limit
    ) {
        limit = clamp(limit, 1, 50);
        includeTags = (includeTags == null) ? List.of() : includeTags;
        excludeTags = (excludeTags == null) ? List.of() : excludeTags;

        // handle rating:... special ids
        boolean allowSuggestive = true;
        boolean allowErotica = true;

        List<String> includeReal = new ArrayList<>();
        for (String t : includeTags) {
            if (t == null) continue;
            if (t.startsWith(RATING_PREFIX)) {
                String v = t.substring(RATING_PREFIX.length()).toLowerCase();
                if (v.equals("suggestive")) allowSuggestive = true;
                if (v.equals("erotica")) allowErotica = true;
            } else {
                includeReal.add(t);
            }
        }

        List<String> excludeReal = new ArrayList<>();
        for (String t : excludeTags) {
            if (t == null) continue;
            if (t.startsWith(RATING_PREFIX)) {
                String v = t.substring(RATING_PREFIX.length()).toLowerCase();
                if (v.equals("suggestive")) allowSuggestive = false;
                if (v.equals("erotica")) allowErotica = false;
            } else {
                excludeReal.add(t);
            }
        }

        StringBuilder sb = new StringBuilder(BASE_URL).append("/manga?limit=").append(limit);

        if (title != null && !title.isBlank()) sb.append("&title=").append(urlEncode(title));

        for (String t : includeReal) if (!t.isBlank()) sb.append("&includedTags[]=").append(urlEncode(t));
        for (String t : excludeReal) if (!t.isBlank()) sb.append("&excludedTags[]=").append(urlEncode(t));

        String orderField = mapSortBy(sortBy);
        if (orderField != null) {
            String dir = "asc".equalsIgnoreCase(sortDir) ? "asc" : "desc";
            sb.append("&order[").append(orderField).append("]=").append(dir);
        }

        // ratings
        sb.append("&contentRating[]=safe");
        if (allowSuggestive) sb.append("&contentRating[]=suggestive");
        if (allowErotica) sb.append("&contentRating[]=erotica");

        try {
            JsonNode root = getJson(sb.toString());
            JsonNode data = root.path("data");
            if (!data.isArray()) return List.of();

            Map<String, String> mangaIdToCoverId = new HashMap<>();
            Set<String> authorIds = new HashSet<>();
            Map<String, String> mangaIdToAuthorId = new HashMap<>();

            for (JsonNode item : data) {
                String id = item.path("id").asText("");
                JsonNode attrs = item.path("attributes");
                if (id.isBlank() || !passesCoreFilters(attrs)) continue;

                String coverId = findCoverId(item.path("relationships"));
                if (coverId != null) mangaIdToCoverId.put(id, coverId);
                else coverUrlCache.putIfAbsent(id, Optional.empty());

                String authorId = findAuthorId(item.path("relationships"));
                if (authorId != null) {
                    authorIds.add(authorId);
                    mangaIdToAuthorId.put(id, authorId);
                }
            }

            Map<String, String> coverIdToFileName =
                    batchFetchCoverFileNames(new ArrayList<>(new HashSet<>(mangaIdToCoverId.values())));

            Map<String, String> authorIdToName = new HashMap<>();
            for (String aid : authorIds) authorIdToName.put(aid, getOrFetchAuthorName(aid));

            List<MangaSearchResult> out = new ArrayList<>();
            for (JsonNode item : data) {
                String id = item.path("id").asText("");
                JsonNode attrs = item.path("attributes");
                if (id.isBlank() || !passesCoreFilters(attrs)) continue;

                String displayTitle = buildDisplayTitle(attrs);
                if (displayTitle.isBlank()) continue;

                String desc = normalizeDescription(attrs.path("description").path("en").asText(""));
                List<String> tags = extractEnglishTags(attrs);

                String coverUrl = getOrCacheCoverUrl(id, mangaIdToCoverId.get(id), coverIdToFileName);

                String author = "";
                String authorId = mangaIdToAuthorId.get(id);
                if (authorId != null) author = authorIdToName.getOrDefault(authorId, "");

                String packedId = ProviderId.of(key(), id).packed();

                out.add(new MangaSearchResult(packedId, displayTitle, desc, coverUrl, 0, tags)
                        .withAuthor(author)
                        .withSource(displayName())
                        .withSourceUrl("https://mangadex.org/title/" + id));
            }

            return out;

        } catch (Exception e) {
            return List.of();
        }
    }

    // random search & search by author name
    private List<MangaSearchResult> searchByAuthorName(String authorName, int limit) {
        // We do:
        // 1) /author?name=... -> get author ids
        // 2) /manga?authors[]=id -> get titles
        // If mangadex changes param, this is the ONLY place you tweak later.
        limit = clamp(limit, 1, 25);
        if (authorName == null || authorName.isBlank()) return List.of();

        try {
            String url = BASE_URL + "/author?limit=5&name=" + urlEncode(authorName.trim());
            JsonNode root = getJson(url);
            JsonNode data = root.path("data");
            if (!data.isArray() || data.isEmpty()) return List.of();

            List<String> authorIds = new ArrayList<>();
            for (JsonNode a : data) {
                String id = a.path("id").asText("");
                if (!id.isBlank()) authorIds.add(id);
                if (authorIds.size() >= 3) break;
            }
            if (authorIds.isEmpty()) return List.of();

            // fetch manga for those authors (small)
            List<MangaSearchResult> out = new ArrayList<>();
            for (String aid : authorIds) {
                String mUrl = BASE_URL + "/manga?limit=" + limit + "&authors[]=" + urlEncode(aid)
                        + "&contentRating[]=safe&contentRating[]=suggestive&contentRating[]=erotica";
                JsonNode mRoot = getJson(mUrl);
                JsonNode mData = mRoot.path("data");
                if (!mData.isArray()) continue;

                // reuse list-building logic by faking "title search" path:
                // We'll just build minimal results quickly.
                for (JsonNode item : mData) {
                    String mangaId = item.path("id").asText("");
                    if (mangaId.isBlank()) continue;

                    JsonNode attrs = item.path("attributes");
                    if (!passesCoreFilters(attrs)) continue;

                    String displayTitle = buildDisplayTitle(attrs);
                    if (displayTitle.isBlank()) continue;

                    List<String> tags = extractEnglishTags(attrs);
                    String desc = normalizeDescription(attrs.path("description").path("en").asText(""));

                    String coverId = findCoverId(item.path("relationships"));
                    String coverUrl = null;
                    if (coverId != null) {
                        // cheap per-item cover fetch; cache protects repeats
                        coverUrl = getOrCacheCoverUrl(mangaId, coverId, null);
                        if (coverUrl == null) {
                            String fileName = fetchCoverFileName(coverId);
                            if (fileName != null && !fileName.isBlank()) coverUrl = buildCoverUrl(mangaId, fileName);
                            coverUrlCache.put(mangaId, Optional.ofNullable(coverUrl));
                        }
                    }

                    String author = getOrFetchAuthorName(aid);

                    String packedId = ProviderId.of(key(), mangaId).packed();
                    out.add(new MangaSearchResult(packedId, displayTitle, desc, coverUrl, 0, tags)
                            .withAuthor(author)
                            .withSource(displayName())
                            .withSourceUrl("https://mangadex.org/title/" + mangaId));
                    if (out.size() >= limit) return out;
                }
            }
            return out;
        } catch (Exception e) {
            return List.of();
        }
    }

    // browse menu
    private List<MangaSearchResult> browseMangaList(String orderQuery, int limit) {
        limit = clamp(limit, 1, 50);
        List<MangaSearchResult> out = new ArrayList<>();

        try {
            String url = BASE_URL + "/manga?limit=" + limit + "&" + orderQuery
                    + "&contentRating[]=safe&contentRating[]=suggestive&contentRating[]=erotica";

            JsonNode root = getJson(url);
            JsonNode data = root.path("data");
            if (!data.isArray()) return out;

            Map<String, String> mangaIdToCoverId = new HashMap<>();
            Set<String> authorIds = new HashSet<>();
            Map<String, String> mangaIdToAuthorId = new HashMap<>();

            for (JsonNode item : data) {
                String id = item.path("id").asText("");
                JsonNode attrs = item.path("attributes");
                if (id.isBlank() || !passesCoreFilters(attrs)) continue;

                String coverId = findCoverId(item.path("relationships"));
                if (coverId != null) mangaIdToCoverId.put(id, coverId);
                else coverUrlCache.putIfAbsent(id, Optional.empty());

                String authorId = findAuthorId(item.path("relationships"));
                if (authorId != null) {
                    authorIds.add(authorId);
                    mangaIdToAuthorId.put(id, authorId);
                }
            }

            Map<String, String> coverIdToFileName =
                    batchFetchCoverFileNames(new ArrayList<>(new HashSet<>(mangaIdToCoverId.values())));

            Map<String, String> authorIdToName = new HashMap<>();
            for (String aid : authorIds) authorIdToName.put(aid, getOrFetchAuthorName(aid));

            for (JsonNode item : data) {
                String id = item.path("id").asText("");
                JsonNode attrs = item.path("attributes");
                if (id.isBlank() || !passesCoreFilters(attrs)) continue;

                String title = buildDisplayTitle(attrs);
                if (title.isBlank()) continue;

                String desc = normalizeDescription(attrs.path("description").path("en").asText(""));
                List<String> tags = extractEnglishTags(attrs);

                String coverUrl = getOrCacheCoverUrl(id, mangaIdToCoverId.get(id), coverIdToFileName);

                String author = "";
                String authorId = mangaIdToAuthorId.get(id);
                if (authorId != null) author = authorIdToName.getOrDefault(authorId, "");

                String packedId = ProviderId.of(key(), id).packed();
                out.add(new MangaSearchResult(packedId, title, desc, coverUrl, 0, tags)
                        .withAuthor(author)
                        .withSource(displayName())
                        .withSourceUrl("https://mangadex.org/title/" + id));
            }

        } catch (Exception ignored) {}

        return out;
    }

    // count chapters
    public int fetchTotalChapters(String mangaId) {
        Integer cached = chapterCountCache.get(mangaId);
        if (cached != null) return cached;

        int count = computeEnglishChapterCountFromAggregate(mangaId);
        chapterCountCache.put(mangaId, count);
        return count;
    }

    private int computeEnglishChapterCountFromAggregate(String mangaId) {
        try {
            String url = BASE_URL + "/manga/" + mangaId + "/aggregate?translatedLanguage[]=en";
            JsonNode root = getJson(url);
            JsonNode volumes = root.path("volumes");
            if (!volumes.isObject()) return 0;

            int total = 0;
            Iterator<Map.Entry<String, JsonNode>> volIt = volumes.fields();
            while (volIt.hasNext()) {
                JsonNode chapters = volIt.next().getValue().path("chapters");
                if (!chapters.isObject()) continue;

                Iterator<String> keys = chapters.fieldNames();
                while (keys.hasNext()) {
                    String key = keys.next();
                    if (key != null && !key.isBlank()) total++;
                }
            }
            return total;
        } catch (Exception ignored) {
            return 0;
        }
    }

    // helpers
    private List<JsonNode> queryMangaByTitle(String title, int limit) throws Exception {
        limit = clamp(limit, 1, 50);
        String url = BASE_URL + "/manga?title=" + urlEncode(title) + "&limit=" + limit
                + "&contentRating[]=safe&contentRating[]=suggestive&contentRating[]=erotica";

        JsonNode root = getJson(url);
        JsonNode data = root.path("data");
        if (!data.isArray()) return List.of();

        List<JsonNode> out = new ArrayList<>();
        for (JsonNode n : data) out.add(n);
        return out;
    }

    private boolean passesCoreFilters(JsonNode attrs) {
        String originalLanguage = attrs.path("originalLanguage").asText("");
        if (!ALLOWED_ORIGINAL_LANGUAGES.contains(originalLanguage)) return false;

        String rating = attrs.path("contentRating").asText("");
        return !"pornographic".equalsIgnoreCase(rating);
    }

    private String buildDisplayTitle(JsonNode attrs) {
        String en = pickTitle(attrs, "en");
        if (!en.isBlank()) return en;

        String fallback = firstTitleValue(attrs.path("title"));
        String ja = pickTitle(attrs, "ja");

        if (!fallback.isBlank() && !ja.isBlank() && !fallback.equals(ja)) return fallback + " (" + ja + ")";
        if (!fallback.isBlank()) return fallback;
        if (!ja.isBlank()) return ja;

        String altAny = firstAltTitleAny(attrs);
        return altAny == null ? "" : altAny;
    }

    private String pickTitle(JsonNode attrs, String lang) {
        String direct = attrs.path("title").path(lang).asText("");
        if (!direct.isBlank()) return direct;

        JsonNode alt = attrs.path("altTitles");
        if (alt.isArray()) {
            for (JsonNode obj : alt) {
                if (obj.has(lang)) {
                    String v = obj.path(lang).asText("");
                    if (!v.isBlank()) return v;
                }
            }
        }
        return "";
    }

    private String firstTitleValue(JsonNode titleObj) {
        if (titleObj != null && titleObj.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> it = titleObj.fields();
            if (it.hasNext()) return it.next().getValue().asText("");
        }
        return "";
    }

    private String firstAltTitleAny(JsonNode attrs) {
        JsonNode alt = attrs.path("altTitles");
        if (!alt.isArray()) return null;
        for (JsonNode obj : alt) {
            Iterator<Map.Entry<String, JsonNode>> it = obj.fields();
            if (it.hasNext()) {
                String v = it.next().getValue().asText("");
                if (!v.isBlank()) return v;
            }
        }
        return null;
    }

    private List<String> collectAltTitles(JsonNode attrs) {
        LinkedHashSet<String> set = new LinkedHashSet<>();
        JsonNode alt = attrs.path("altTitles");
        if (alt.isArray()) {
            for (JsonNode obj : alt) {
                Iterator<Map.Entry<String, JsonNode>> it = obj.fields();
                while (it.hasNext()) {
                    String v = it.next().getValue().asText("");
                    if (!v.isBlank()) set.add(v);
                    if (set.size() >= 14) break;
                }
                if (set.size() >= 14) break;
            }
        }
        return new ArrayList<>(set);
    }

    private List<String> extractEnglishTags(JsonNode attrs) {
        LinkedHashSet<String> out = new LinkedHashSet<>();
        JsonNode tags = attrs.path("tags");
        if (!tags.isArray()) return new ArrayList<>();

        for (JsonNode t : tags) {
            String name = t.path("attributes").path("name").path("en").asText("");
            if (name.isBlank()) continue;

            if (isHardBlockedTagName(name)) continue;

            out.add(name);
            if (out.size() >= 12) break;
        }
        return new ArrayList<>(out);
    }

    private String normalizeDescription(String desc) {
        if (desc == null) return "";
        String s = desc.replace("\r", "").trim();
        s = s.replaceAll("[ \t]+", " ");
        return s;
    }

    private String findCoverId(JsonNode relationships) {
        if (relationships == null || !relationships.isArray()) return null;
        for (JsonNode rel : relationships) {
            if ("cover_art".equals(rel.path("type").asText(""))) {
                String id = rel.path("id").asText(null);
                if (id != null && !id.isBlank()) return id;
            }
        }
        return null;
    }

    private String findAuthorId(JsonNode relationships) {
        if (relationships == null || !relationships.isArray()) return null;
        for (JsonNode rel : relationships) {
            if ("author".equals(rel.path("type").asText(""))) {
                String id = rel.path("id").asText(null);
                if (id != null && !id.isBlank()) return id;
            }
        }
        return null;
    }

    private String getOrFetchAuthorName(String authorId) {
        Optional<String> cached = authorNameCache.get(authorId);
        if (cached != null) return cached.orElse("");

        String name = "";
        try {
            JsonNode root = getJson(BASE_URL + "/author/" + authorId);
            name = root.path("data").path("attributes").path("name").asText("");
        } catch (Exception ignored) {}

        authorNameCache.put(authorId, Optional.ofNullable(name.isBlank() ? null : name));
        return name;
    }

    private String fetchCoverFileName(String coverId) {
        try {
            JsonNode root = getJson(BASE_URL + "/cover/" + coverId);
            return root.path("data").path("attributes").path("fileName").asText("");
        } catch (Exception e) {
            return "";
        }
    }

    private Map<String, String> batchFetchCoverFileNames(List<String> coverIds) {
        Map<String, String> out = new HashMap<>();
        if (coverIds == null || coverIds.isEmpty()) return out;

        int chunkSize = 60;
        for (int i = 0; i < coverIds.size(); i += chunkSize) {
            List<String> chunk = coverIds.subList(i, Math.min(i + chunkSize, coverIds.size()));
            try {
                StringBuilder url = new StringBuilder(BASE_URL).append("/cover?limit=").append(chunk.size());
                for (String id : chunk) url.append("&ids[]=").append(urlEncode(id));

                JsonNode root = getJson(url.toString());
                JsonNode data = root.path("data");
                if (!data.isArray()) continue;

                for (JsonNode item : data) {
                    String id = item.path("id").asText("");
                    String fileName = item.path("attributes").path("fileName").asText("");
                    if (!id.isBlank() && !fileName.isBlank()) out.put(id, fileName);
                }
            } catch (Exception ignored) {}
        }

        return out;
    }

    private String buildCoverUrl(String mangaId, String fileName) {
        return "https://uploads.mangadex.org/covers/" + mangaId + "/" + fileName + ".256.jpg";
    }

    private String getOrCacheCoverUrl(String mangaId, String coverId, Map<String, String> coverIdToFileName) {
        Optional<String> cached = coverUrlCache.get(mangaId);
        if (cached != null) return cached.orElse(null);

        String coverUrl = null;
        if (coverId != null) {
            String fileName = (coverIdToFileName == null) ? null : coverIdToFileName.get(coverId);
            if (fileName != null && !fileName.isBlank()) {
                coverUrl = buildCoverUrl(mangaId, fileName);
            }
        }
        coverUrlCache.put(mangaId, Optional.ofNullable(coverUrl));
        return coverUrl;
    }

    // HTTP Status checks and response
    private JsonNode getJson(String url) throws Exception {
        int maxAttempts = 4;
        long backoffMs = 250;

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(12))
                    .header("Accept", "application/json")
                    .header("User-Agent", "mangatracker/1.0 (joshua)")
                    .GET()
                    .build();

            HttpResponse<String> res = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            int code = res.statusCode();

            if (code >= 200 && code < 300) {
                return objectMapper.readTree(res.body());
            }

            boolean retryable = (code == 429) || (code >= 500 && code <= 599);
            if (!retryable || attempt == maxAttempts) {
                throw new RuntimeException("MangaDex HTTP " + code + " for: " + url + " body=" + safeSnippet(res.body()));
            }

            long sleepMs = backoffMs;
            Optional<String> retryAfter = res.headers().firstValue("Retry-After");
            if (retryAfter.isPresent()) {
                try {
                    long seconds = Long.parseLong(retryAfter.get().trim());
                    sleepMs = Math.max(sleepMs, seconds * 1000L);
                } catch (NumberFormatException ignored) {}
            }

            try { Thread.sleep(sleepMs); } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Interrupted while retrying MangaDex request");
            }

            backoffMs *= 2;
        }

        throw new RuntimeException("Unreachable");
    }

    private String safeSnippet(String body) {
        if (body == null) return "";
        String s = body.replace("\n", " ").replace("\r", " ").trim();
        return (s.length() <= 240) ? s : s.substring(0, 240) + "...";
    }

    private String urlEncode(String s) {
        return URLEncoder.encode(s == null ? "" : s, StandardCharsets.UTF_8);
    }

    private int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    private String mapSortBy(String sortBy) {
        if (sortBy == null) return null;
        return switch (sortBy.toLowerCase()) {
            case "relevance" -> null;
            case "latest", "updated" -> "latestUploadedChapter";
            case "new", "created" -> "createdAt";
            case "rating" -> "rating";
            case "follows", "popular" -> "followedCount";
            case "title" -> "title";
            default -> null;
        };
    }
}
