package org.grandparents.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "ratings")
public class Rating {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "rater_id", nullable = false)
    private Long raterId; // кто оценивает

    @Column(name = "target_id", nullable = false)
    private Long targetId; // кого оценивают (автор)

    @Column(name = "elder_id", nullable = false)
    private Long elderId; // заявка

    @Column(name = "stars", nullable = false)
    private Integer stars; // 1–5

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    // ===== ГЕТТЕРЫ И СЕТТЕРЫ =====
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getRaterId() { return raterId; }
    public void setRaterId(Long raterId) { this.raterId = raterId; }

    public Long getTargetId() { return targetId; }
    public void setTargetId(Long targetId) { this.targetId = targetId; }

    public Long getElderId() { return elderId; }
    public void setElderId(Long elderId) { this.elderId = elderId; }

    public Integer getStars() { return stars; }
    public void setStars(Integer stars) { this.stars = stars; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}