package com.joshua.mangatracker.model;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "user_manga")
public class UserManga {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    private String mangaId;
    private String title;
    private String coverUrl;

    private String source;     // "md" / "mal"
    private String sourceId;   // raw id
    private String externalReadUrl; // optional

    private int currentChapter;

    private Integer myScore;          // nullable
    private String status;            // READING / ON_HOLD / PLAN_TO_READ / DROPPED

    private Instant lastAdded;
    private Instant lastRead;

    @Transient
    private Integer latestChapter;    // computed on read, not stored

    public UserManga() {}

    public UserManga(String mangaId, String title, String coverUrl) {
        this.mangaId = mangaId;
        this.title = title;
        this.coverUrl = coverUrl;
        this.currentChapter = 0;
        this.status = "READING";
        this.lastAdded = Instant.now();
        this.lastRead = null;
        this.myScore = null;
    }

    public Long getId() { return id; }

    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }

    public String getMangaId() { return mangaId; }
    public String getTitle() { return title; }
    public String getCoverUrl() { return coverUrl; }

    public int getCurrentChapter() { return currentChapter; }
    public void setCurrentChapter(int chapter) { this.currentChapter = chapter; }

    public Integer getMyScore() { return myScore; }
    public void setMyScore(Integer myScore) { this.myScore = myScore; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Instant getLastAdded() { return lastAdded; }
    public void setLastAdded(Instant lastAdded) { this.lastAdded = lastAdded; }

    public Instant getLastRead() { return lastRead; }
    public void setLastRead(Instant lastRead) { this.lastRead = lastRead; }

    public Integer getLatestChapter() { return latestChapter; }
    public void setLatestChapter(Integer latestChapter) { this.latestChapter = latestChapter; }
}
