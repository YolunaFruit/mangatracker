package com.joshua.mangatracker.repository;

import com.joshua.mangatracker.model.Manga;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MangaRepository extends JpaRepository<Manga, Long> {
    Manga findByTitle(String title);
}
