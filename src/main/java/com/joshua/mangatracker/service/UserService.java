package com.joshua.mangatracker.service;

import com.joshua.mangatracker.model.User;
import com.joshua.mangatracker.model.UserManga;
import com.joshua.mangatracker.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import com.joshua.mangatracker.UrlSafety;
import com.joshua.mangatracker.service.providers.ProviderId;

@Service
@Transactional
public class UserService {

    private final UserRepository userRepo;
    private final MangaDexService mangaService;

    private final PasswordEncoder passwords;

    public UserService(UserRepository userRepo, MangaDexService mangaService, PasswordEncoder passwords) {
        this.passwords = passwords;
        this.userRepo = userRepo;
        this.mangaService = mangaService;
    }

    public User register(String username, String password) {
        username = username == null ? "" : username.trim();
        if (!username.matches("[A-Za-z0-9_-]{3,40}"))
            throw new IllegalArgumentException("Use 3–40 letters, numbers, underscores or hyphens for your username.");
        if (password == null || password.length() < 8 || password.getBytes(StandardCharsets.UTF_8).length > 72)
            throw new IllegalArgumentException("Use a password of at least 8 characters and at most 72 UTF-8 bytes.");
        User exists = userRepo.findByUsername(username);
        if (exists != null) return null;
        return userRepo.save(new User(username, passwords.encode(password)));
    }

    public User login(String username, String password) {
        if (username == null || password == null || password.getBytes(StandardCharsets.UTF_8).length > 72) return null;
        User u = userRepo.findByUsername(username.trim());
        if (u == null || u.getPassword() == null) return null;
        String stored = u.getPassword();
        if (stored.startsWith("$2")) {
            if (!passwords.matches(password, stored)) return null;
        } else {
            // Upgrade existing local accounts on their first successful login.
            if (!MessageDigest.isEqual(stored.getBytes(StandardCharsets.UTF_8), password.getBytes(StandardCharsets.UTF_8))) return null;
            u.setPassword(passwords.encode(password));
            userRepo.save(u);
        }
        return u;
    }

    public User findByUsername(String username) {
        return userRepo.findByUsername(username);
    }

    public User findById(Long id) {
        return userRepo.findById(id).orElse(null);
    }

    public User save(User user) {
        return userRepo.save(user);
    }

    /**
     * NEW: Add a manga without calling MangaDex (frontend sends title/coverUrl).
     */
    public User addMangaBasic(Long userId, String mangaId, String title, String coverUrl) {
        ProviderId.parseCompatible(mangaId); // Validate before persisting a provider identifier.
        if (title == null || title.isBlank() || title.length() > 500)
            throw new IllegalArgumentException("A manga title of at most 500 characters is required.");
        User u = userRepo.findById(userId).orElse(null);
        if (u == null) throw new IllegalArgumentException("User not found.");

        boolean already = u.getTrackedManga().stream()
                .anyMatch(x -> x.getMangaId() != null && x.getMangaId().equals(mangaId));
        if (already) return u;

        String safeTitle = (title == null || title.isBlank()) ? "(untitled)" : title;

        UserManga userManga = new UserManga(mangaId, safeTitle, UrlSafety.normalizeHttpUrlOrNull(coverUrl));
        userManga.setLastAdded(Instant.now());
        userManga.setLastRead(null);

        // IMPORTANT: ensure relationship is set
        u.addManga(userManga);

        return userRepo.save(u);
    }

    /**
     * OLD: Add a manga by fetching details from MangaDex.
     * (This may fail right now due to your MangaDex API issue.)
     */
    public User addManga(Long userId, String mangaId) {
        User u = userRepo.findById(userId).orElse(null);
        if (u == null) throw new RuntimeException("User not found for id=" + userId);

        boolean already = u.getTrackedManga().stream().anyMatch(x -> x.getMangaId().equals(mangaId));
        if (already) return u;

        var details = mangaService.fetchMangaDetails(mangaId);
        if (details == null) throw new RuntimeException("MangaDex details fetch failed.");

        UserManga userManga = new UserManga(mangaId, details.getTitle(), details.getCoverUrl());
        u.addManga(userManga);

        return userRepo.save(u);
    }
}
