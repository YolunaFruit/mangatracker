package com.joshua.mangatracker.service.providers;

import java.util.Objects;
import java.util.UUID;
import java.util.Locale;

public final class ProviderId {
    public final String provider; // md / mal
    public final String rawId;     // md uuid OR mal numeric string

    private ProviderId(String provider, String rawId) {
        this.provider = provider;
        this.rawId = rawId;
    }

    public static ProviderId of(String provider, String rawId) {
        if (provider == null || provider.isBlank()) throw new IllegalArgumentException("provider required");
        if (rawId == null || rawId.isBlank()) throw new IllegalArgumentException("rawId required");
        provider = provider.trim().toLowerCase(Locale.ROOT);
        rawId = rawId.trim();
        if (provider.equals("md")) {
            if (!UUID.fromString(rawId).toString().equalsIgnoreCase(rawId)) throw new IllegalArgumentException("Invalid MangaDex id.");
        } else if (provider.equals("mal")) {
            if (!rawId.matches("[1-9][0-9]{0,9}")) throw new IllegalArgumentException("Invalid MyAnimeList id.");
        } else throw new IllegalArgumentException("Unknown manga provider.");
        return new ProviderId(provider, rawId);
    }

    /** Older local libraries stored bare MangaDex UUIDs. */
    public static ProviderId parseCompatible(String id) {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("Manga id is required.");
        return id.contains(":") ? parsePacked(id) : of("md", id);
    }

    /** "md:uuid" / "mal:12345" */
    public String packed() {
        return provider + ":" + rawId;
    }

    public static ProviderId parsePacked(String packed) {
        if (packed == null) throw new IllegalArgumentException("id required");
        int idx = packed.indexOf(':');
        if (idx <= 0 || idx >= packed.length() - 1) throw new IllegalArgumentException("Bad id: " + packed);
        return of(packed.substring(0, idx), packed.substring(idx + 1));
    }

    @Override public boolean equals(Object o) {
        if (!(o instanceof ProviderId other)) return false;
        return Objects.equals(provider, other.provider) && Objects.equals(rawId, other.rawId);
    }
    @Override public int hashCode() { return Objects.hash(provider, rawId); }
}
