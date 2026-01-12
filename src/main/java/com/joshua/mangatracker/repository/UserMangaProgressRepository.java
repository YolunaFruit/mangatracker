package com.joshua.mangatracker.repository;

import com.joshua.mangatracker.model.User;
import com.joshua.mangatracker.model.Manga;
import com.joshua.mangatracker.model.UserMangaProgress;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface UserMangaProgressRepository extends JpaRepository<UserMangaProgress, Long> {

    List<UserMangaProgress> findByUser(User user);

    UserMangaProgress findByUserAndManga(User user, Manga manga);
}
