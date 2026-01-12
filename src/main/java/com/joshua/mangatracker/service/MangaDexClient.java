package com.joshua.mangatracker.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

@Component
public class MangaDexClient {

    private final HttpClient httpClient;
    private final ObjectMapper mapper;

    public MangaDexClient() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(8))
                .build();
        this.mapper = new ObjectMapper();
    }

    public JsonNode getJson(String url) throws IOException, InterruptedException {
        return getJson(url, 3);
    }

    /**
     * Robust GET:
     * - checks status codes
     * - retries on 429 + 5xx with exponential backoff
     * - guards against non-JSON responses
     */
    public JsonNode getJson(String url, int maxRetries) throws IOException, InterruptedException {
        int attempt = 0;
        long backoffMs = 250;

        while (true) {
            attempt++;

            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(12))
                    .header("Accept", "application/json")
                    .GET()
                    .build();

            HttpResponse<String> res = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            int code = res.statusCode();
            String body = res.body() == null ? "" : res.body();

            // Success
            if (code >= 200 && code < 300) {
                try {
                    return mapper.readTree(body);
                } catch (Exception parseErr) {
                    // Body might be HTML or partial response — treat as retryable if we can
                    if (attempt <= maxRetries) {
                        Thread.sleep(backoffMs);
                        backoffMs *= 2;
                        continue;
                    }
                    throw new IOException("MangaDex returned non-JSON body (parse failed).");
                }
            }

            boolean retryable = (code == 429) || (code >= 500 && code <= 599);
            if (retryable && attempt <= maxRetries) {
                // Respect Retry-After if present
                long sleepMs = backoffMs;
                String retryAfter = res.headers().firstValue("Retry-After").orElse("");
                if (!retryAfter.isBlank()) {
                    try {
                        // seconds per RFC
                        sleepMs = Math.max(sleepMs, Long.parseLong(retryAfter) * 1000L);
                    } catch (NumberFormatException ignored) {}
                }

                Thread.sleep(sleepMs);
                backoffMs *= 2;
                continue;
            }

            // Non-retryable or out of retries
            throw new IOException("MangaDex request failed. HTTP " + code + " for " + url);
        }
    }
}
