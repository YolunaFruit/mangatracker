package com.joshua.mangatracker.controller;

import com.joshua.mangatracker.model.User;
import com.joshua.mangatracker.model.UserManga;
import com.joshua.mangatracker.service.UserService;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.time.Instant;
import java.util.Locale;
import java.util.Set;

@RestController
@RequestMapping("/progress")
@PreAuthorize("#username == authentication.name")
public class ProgressController {
    private static final Set<String> STATUSES = Set.of("READING", "COMPLETED", "ON_HOLD", "PLAN_TO_READ", "DROPPED");
    private final UserService users;

    public ProgressController(UserService users) { this.users = users; }

    @GetMapping("/{username}")
    public User getUserLibrary(@PathVariable String username) { return user(username); }

    @PostMapping("/{username}/update")
    public User updateProgress(@PathVariable String username, @RequestParam String mangaId, @RequestParam int chapter) {
        if (chapter < 0 || chapter > 1000000) throw new IllegalArgumentException("Chapter must be between 0 and 1,000,000.");
        User user = user(username);
        UserManga manga = manga(user, mangaId);
        // Metadata may be unavailable or only list translated chapters. Never discard manual progress.
        manga.setCurrentChapter(chapter);
        manga.setLastRead(Instant.now());
        return users.save(user);
    }

    @PostMapping("/{username}/score")
    public User setScore(@PathVariable String username, @RequestParam String mangaId, @RequestParam int score) {
        if (score < -1 || score > 10) throw new IllegalArgumentException("Score must be 0–10, or -1 to clear it.");
        User user = user(username);
        manga(user, mangaId).setMyScore(score == -1 ? null : score);
        return users.save(user);
    }

    @PostMapping("/{username}/status")
    public User setStatus(@PathVariable String username, @RequestParam String mangaId, @RequestParam String status) {
        String normalized = status.trim().toUpperCase(Locale.ROOT);
        if (!STATUSES.contains(normalized)) throw new IllegalArgumentException("Unknown reading status.");
        User user = user(username);
        manga(user, mangaId).setStatus(normalized);
        return users.save(user);
    }

    @PostMapping("/{username}/entry")
    public User editEntry(@PathVariable String username, @RequestBody EditEntry request) {
        if (request.chapter() < 0 || request.chapter() > 1000000)
            throw new IllegalArgumentException("Chapter must be between 0 and 1,000,000.");
        String status = request.status() == null ? "" : request.status().toUpperCase(Locale.ROOT);
        if (!STATUSES.contains(status)) throw new IllegalArgumentException("Unknown reading status.");
        if (request.score() != null && (request.score() < 0 || request.score() > 10))
            throw new IllegalArgumentException("Score must be 0–10.");
        User user = user(username);
        UserManga manga = manga(user, request.mangaId());
        manga.setCurrentChapter(request.chapter());
        manga.setStatus(status);
        manga.setMyScore(request.score());
        manga.setLastRead(Instant.now());
        return users.save(user);
    }

    public record EditEntry(String mangaId, int chapter, String status, Integer score) {}

    @PostMapping("/{username}/remove")
    public User removeManga(@PathVariable String username, @RequestParam String mangaId) {
        User user = user(username);
        manga(user, mangaId);
        user.getTrackedManga().removeIf(m -> m.getMangaId().equals(mangaId));
        return users.save(user);
    }

    private User user(String username) {
        User user = users.findByUsername(username);
        if (user == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found.");
        return user;
    }

    private UserManga manga(User user, String id) {
        return user.getTrackedManga().stream().filter(m -> m.getMangaId().equals(id)).findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Manga is not in your library."));
    }
}
