package com.joshua.mangatracker.controller;

import com.joshua.mangatracker.model.Manga;
import com.joshua.mangatracker.service.MangaService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/manga")
@CrossOrigin
public class MangaController {

    private final MangaService mangaService;

    public MangaController(MangaService mangaService) {
        this.mangaService = mangaService;
    }

    @GetMapping("/add")
    public Manga addManga(@RequestParam String title,
                          @RequestParam String externalUrl,
                          @RequestParam String mangaDexId) {
        return mangaService.addManga(title, externalUrl, mangaDexId);
    }



    @GetMapping("/id/{id}")
    public Manga getMangaById(@PathVariable Long id) {
        return mangaService.findById(id);
    }
}
