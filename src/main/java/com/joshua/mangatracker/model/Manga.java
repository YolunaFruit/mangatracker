package com.joshua.mangatracker.model;

import jakarta.persistence.*;

@Entity
@Table(name = "manga")
public class Manga {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String title;

    private String externalUrl; // link to the manga reader site

    private int totalChapters; // fetched from API later

    public Manga() {}

    public Manga(String title, String externalUrl, int totalChapters) {
        this.title = title;
        this.externalUrl = externalUrl;
        this.totalChapters = totalChapters;
    }

    // Getters & Setters
    public Long getId() { return id; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getExternalUrl() { return externalUrl; }
    public void setExternalUrl(String externalUrl) { this.externalUrl = externalUrl; }

    public int getTotalChapters() { return totalChapters; }
    public void setTotalChapters(int totalChapters) { this.totalChapters = totalChapters; }
}
