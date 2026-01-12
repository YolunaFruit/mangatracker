package com.joshua.mangatracker.service;

import com.joshua.mangatracker.model.User;
import com.joshua.mangatracker.model.UserManga;
import com.joshua.mangatracker.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
public class UserService {

    private final UserRepository userRepo;
    private final MangaDexService mangaService;

    public UserService(UserRepository userRepo, MangaDexService mangaService) {
        this.userRepo = userRepo;
        this.mangaService = mangaService;
    }

    public User register(String username, String password) {
        User exists = userRepo.findByUsername(username);
        if (exists != null) return null;
        return userRepo.save(new User(username, password));
    }

    public User login(String username, String password) {
        User u = userRepo.findByUsername(username);
        if (u == null) return null;
        if (!u.getPassword().equals(password)) return null;
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
        User u = userRepo.findById(userId).orElse(null);
        if (u == null) throw new RuntimeException("User not found for id=" + userId);

        boolean already = u.getTrackedManga().stream()
                .anyMatch(x -> x.getMangaId() != null && x.getMangaId().equals(mangaId));
        if (already) return u;

        String safeTitle = (title == null || title.isBlank()) ? "(untitled)" : title;

        UserManga userManga = new UserManga(mangaId, safeTitle, coverUrl);
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
