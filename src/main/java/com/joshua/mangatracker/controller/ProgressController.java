package com.joshua.mangatracker.controller;

import com.joshua.mangatracker.model.User;
import com.joshua.mangatracker.model.UserManga;
import com.joshua.mangatracker.service.MangaDexService;
import com.joshua.mangatracker.service.UserService;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;

@RestController
@RequestMapping("/progress")
@CrossOrigin
public class ProgressController {

    private final UserService userService;
    private final MangaDexService mangaDexService;

    public ProgressController(UserService userService, MangaDexService mangaDexService) {
        this.userService = userService;
        this.mangaDexService = mangaDexService;
    }

    // Get all manga for a user (and attach latestChapter transiently)
    @GetMapping("/{username}")
    public User getUserLibrary(@PathVariable String username) {
        User user = userService.findByUsername(username);
        if (user == null) return null;

        for (UserManga m : user.getTrackedManga()) {
            try {
                int latest = mangaDexService.fetchTotalChapters(m.getMangaId());
                m.setLatestChapter(latest);
            } catch (Exception ignored) {}
        }
        return user;
    }

    @PostMapping("/{username}/update")
    public User updateProgress(
            @PathVariable String username,
            @RequestParam String mangaId,
            @RequestParam int chapter
    ) {
        User user = userService.findByUsername(username);
        if (user == null) return null;

        for (UserManga m : user.getTrackedManga()) {
            if (m.getMangaId().equals(mangaId)) {
                int latest = mangaDexService.fetchTotalChapters(mangaId);
                if (chapter > latest) chapter = latest;
                if (chapter < 0) chapter = 0;

                m.setCurrentChapter(chapter);
                m.setLastRead(Instant.now());
                m.setLatestChapter(latest);
                break;
            }
        }

        return userService.save(user);
    }

    @PostMapping("/{username}/score")
    public User setScore(
            @PathVariable String username,
            @RequestParam String mangaId,
            @RequestParam int score
    ) {
        User user = userService.findByUsername(username);
        if (user == null) return null;

        for (UserManga m : user.getTrackedManga()) {
            if (m.getMangaId().equals(mangaId)) {
                if (score < 0) m.setMyScore(null);
                else m.setMyScore(Math.min(score, 10));
                break;
            }
        }
        return userService.save(user);
    }

    @PostMapping("/{username}/status")
    public User setStatus(
            @PathVariable String username,
            @RequestParam String mangaId,
            @RequestParam String status
    ) {
        User user = userService.findByUsername(username);
        if (user == null) return null;

        String normalized = (status == null) ? "READING" : status.trim().toUpperCase();

        for (UserManga m : user.getTrackedManga()) {
            if (m.getMangaId().equals(mangaId)) {
                m.setStatus(normalized);
                break;
            }
        }
        return userService.save(user);
    }

    @PostMapping("/{username}/remove")
    public User removeManga(@PathVariable String username, @RequestParam String mangaId) {
        User user = userService.findByUsername(username);
        if (user == null) return null;

        user.getTrackedManga().removeIf(x -> x.getMangaId().equals(mangaId));
        return userService.save(user);
    }
}
