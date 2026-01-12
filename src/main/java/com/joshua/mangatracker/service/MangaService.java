package com.joshua.mangatracker.service;

import com.joshua.mangatracker.model.Manga;
import com.joshua.mangatracker.repository.MangaRepository;
import org.springframework.stereotype.Service;

@Service
public class MangaService {

    private final MangaRepository mangaRepository;
    private final MangaDexService mangaDexService;

    public MangaService(MangaRepository mangaRepository,
                        MangaDexService mangaDexService) {
        this.mangaRepository = mangaRepository;
        this.mangaDexService = mangaDexService;
    }

    public Manga addManga(String title, String externalUrl, String mangaDexId) {

        // Fetch total chapters from MangaDex
        int totalChapters = mangaDexService.fetchTotalChapters(mangaDexId);

        Manga m = new Manga(title, externalUrl, totalChapters);

        return mangaRepository.save(m);
    }

    public Manga findById(Long id) {
        return mangaRepository.findById(id).orElse(null);
    }

}
