package com.joshua.mangatracker.model;

import jakarta.persistence.*;

@Entity
@Table(name = "user_manga_progress")
public class UserMangaProgress {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    private User user;

    @ManyToOne
    private Manga manga;

    private int currentChapter;

    public UserMangaProgress() {}

    public UserMangaProgress(User user, Manga manga, int currentChapter) {
        this.user = user;
        this.manga = manga;
        this.currentChapter = currentChapter;
    }

    // Getters & Setters
    public Long getId() { return id; }

    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }

    public Manga getManga() { return manga; }
    public void setManga(Manga manga) { this.manga = manga; }

    public int getCurrentChapter() { return currentChapter; }
    public void setCurrentChapter(int currentChapter) { this.currentChapter = currentChapter; }
}
