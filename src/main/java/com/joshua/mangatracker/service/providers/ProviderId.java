package com.joshua.mangatracker.service.providers;

import java.util.Objects;

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
        return new ProviderId(provider.trim().toLowerCase(), rawId.trim());
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
