package com.joshua.mangatracker.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.joshua.mangatracker.dto.MangaSearchResult;
import com.joshua.mangatracker.service.providers.MangaProvider;
import com.joshua.mangatracker.service.providers.ProviderId;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Service
public class MyAnimeListService implements MangaProvider {

    private static final String BASE_URL = "https://api.myanimelist.net/v2";
    private final HttpClient http;
    private final ObjectMapper om;

    @Value("${mal.clientId:}")
    private String clientId;

    public MyAnimeListService() {
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();
        this.om = new ObjectMapper();
    }

    @Override public String key() { return "mal"; }
    public String displayName() { return "MyAnimeList"; }

    private boolean enabled() {
        return clientId != null && !clientId.isBlank();
    }

    @Override
    public List<MangaSearchResult> search(String query, int limit) {
        if (!enabled()) return List.of();
        limit = Math.max(1, Math.min(limit, 50));
        if (query == null || query.isBlank()) return List.of();

        // MAL search: /manga?q=...&limit=...&fields=...
        // fields varies by docs; below works for common v2 usage.
        String fields = "synopsis,main_picture,num_chapters,authors,genres";
        String url = BASE_URL + "/manga?q=" + enc(query.trim()) + "&limit=" + limit + "&fields=" + enc(fields);

        try {
            JsonNode root = getJson(url);
            JsonNode data = root.path("data");
            if (!data.isArray()) return List.of();

            List<MangaSearchResult> out = new ArrayList<>();
            for (JsonNode row : data) {
                JsonNode node = row.path("node");
                String id = node.path("id").asText("");
                String title = node.path("title").asText("");
                if (id.isBlank() || title.isBlank()) continue;

                String synopsis = node.path("synopsis").asText("");
                String cover = node.path("main_picture").path("large").asText("");
                if (cover.isBlank()) cover = node.path("main_picture").path("medium").asText("");

                int numCh = node.path("num_chapters").asInt(0);
                String author = extractAuthor(node);

                var packedId = ProviderId.of(key(), id).packed();

                out.add(new MangaSearchResult(
                        packedId,
                        title,
                        synopsis,
                        cover,
                        numCh,
                        List.of() // keep MAL tags empty for now
                ).withAuthor(author)
                 .withSource(displayName())
                 .withSourceUrl("https://myanimelist.net/manga/" + id));
            }
            return out;
        } catch (Exception e) {
            return List.of();
        }
    }

    @Override
    public MangaSearchResult details(String providerRawId) {
        if (!enabled()) return null;
        if (providerRawId == null || providerRawId.isBlank()) return null;

        // /manga/{id}?fields=...
        String fields = "synopsis,main_picture,num_chapters,authors,genres,alternative_titles";
        String url = BASE_URL + "/manga/" + enc(providerRawId.trim()) + "?fields=" + enc(fields);

        try {
            JsonNode node = getJson(url);

            String id = node.path("id").asText(providerRawId);
            String title = node.path("title").asText("");
            String synopsis = node.path("synopsis").asText("");
            String cover = node.path("main_picture").path("large").asText("");
            if (cover.isBlank()) cover = node.path("main_picture").path("medium").asText("");
            int numCh = node.path("num_chapters").asInt(0);
            String author = extractAuthor(node);

            var packedId = ProviderId.of(key(), id).packed();

            MangaSearchResult out = new MangaSearchResult(
                    packedId, title, synopsis, cover, numCh, List.of()
            );
            out.setAuthor(author);
            out.setSource(displayName());
            out.setSourceUrl("https://myanimelist.net/manga/" + id);

            // alt titles
            List<String> alt = new ArrayList<>();
            JsonNode altTitles = node.path("alternative_titles");
            if (altTitles.isObject()) {
                String en = altTitles.path("en").asText("");
                String ja = altTitles.path("ja").asText("");
                String syn = altTitles.path("synonyms").isArray() ? altTitles.path("synonyms").toString() : "";
                if (!en.isBlank()) alt.add(en);
                if (!ja.isBlank()) alt.add(ja);
                // keep it simple; you can parse synonyms later if you want
            }
            out.setAltTitles(alt);

            return out;
        } catch (Exception e) {
            return null;
        }
    }

    private String extractAuthor(JsonNode node) {
        // MAL returns authors[]; we format as "First Last" (or name field if present)
        JsonNode authors = node.path("authors");
        if (!authors.isArray() || authors.isEmpty()) return "";
        JsonNode a0 = authors.get(0);

        // Some MAL shapes: { node: { first_name,last_name } } or { first_name,last_name }
        JsonNode an = a0.has("node") ? a0.path("node") : a0;

        String first = an.path("first_name").asText("");
        String last = an.path("last_name").asText("");
        String name = an.path("name").asText("");

        String full = (first + " " + last).trim();
        if (!full.isBlank()) return full;
        if (!name.isBlank()) return name;
        return "";
    }

    private JsonNode getJson(String url) throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(12))
                .header("X-MAL-CLIENT-ID", clientId)
                .header("Accept", "application/json")
                .GET()
                .build();
        HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() < 200 || res.statusCode() >= 300) {
            throw new RuntimeException("MAL HTTP " + res.statusCode() + " body=" + snippet(res.body()));
        }
        return om.readTree(res.body());
    }

    private String enc(String s) {
        return URLEncoder.encode(s == null ? "" : s, StandardCharsets.UTF_8);
    }

    private String snippet(String body) {
        if (body == null) return "";
        String s = body.replace("\n", " ").replace("\r", " ").trim();
        return s.length() <= 240 ? s : s.substring(0, 240) + "...";
    }
}
