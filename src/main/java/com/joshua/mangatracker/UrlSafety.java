package com.joshua.mangatracker;

import java.net.URI;

public final class UrlSafety {
    private UrlSafety() {}

    public static String normalizeHttpUrlOrNull(String url) {
        if (url == null) return null;
        String s = url.trim();
        if (s.isBlank()) return null;
        if (s.length() > 2048) return null;

        try {
            URI u = URI.create(s);
            String scheme = (u.getScheme() == null) ? "" : u.getScheme().toLowerCase();
            if (!scheme.equals("http") && !scheme.equals("https")) return null;

            if (u.getHost() == null || u.getHost().isBlank()) return null;

            // Prevent credentials-in-URL
            if (u.getUserInfo() != null) return null;

            return u.toString();
        } catch (Exception e) {
            return null;
        }
    }
}
