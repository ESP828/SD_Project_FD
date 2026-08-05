package com.example.backend.favorite.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "favorite")
public class Favorite {

    @EmbeddedId
    private FavoriteId id;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected Favorite() {
    }

    public Favorite(Long accountId, Long restaurantId) {
        this.id = new FavoriteId(accountId, restaurantId);
    }

    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now();
    }

    public FavoriteId getId() {
        return id;
    }

    public Long getAccountId() {
        return id.getAccountId();
    }

    public Long getRestaurantId() {
        return id.getRestaurantId();
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
