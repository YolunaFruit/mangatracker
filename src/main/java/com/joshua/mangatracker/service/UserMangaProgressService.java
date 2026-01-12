package com.joshua.mangatracker.service;

import com.joshua.mangatracker.model.User;
import com.joshua.mangatracker.model.Manga;
import com.joshua.mangatracker.model.UserMangaProgress;
import com.joshua.mangatracker.repository.UserMangaProgressRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class UserMangaProgressService {

    private final UserMangaProgressRepository progressRepository;

    public UserMangaProgressService(UserMangaProgressRepository progressRepository) {
        this.progressRepository = progressRepository;
    }

    public UserMangaProgress addOrUpdateProgress(User user, Manga manga, int currentChapter) {
        UserMangaProgress progress = progressRepository.findByUserAndManga(user, manga);

        if (progress == null) {
            progress = new UserMangaProgress(user, manga, currentChapter);
        } else {
            progress.setCurrentChapter(currentChapter);
        }

        return progressRepository.save(progress);
    }

    public List<UserMangaProgress> getProgressForUser(User user) {
        return progressRepository.findByUser(user);
    }
}
