package com.example.backend.review.domain.entity;

import com.example.backend.auth.domain.entity.Account;
import com.example.backend.restaurant.domain.entity.PublicRestaurant;
import com.example.backend.restaurant.domain.entity.Restaurant;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 음식점 리뷰. 우리 사이트에 사업자가 직접 등록한 {@link Restaurant}뿐 아니라
 * 공공데이터 출처 {@link PublicRestaurant}에도 남길 수 있다.
 * restaurant, publicRestaurant 중 정확히 하나만 채워진다.
 */
@Entity
@Table(name = "review")
public class Review {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "review_id")
    private Long reviewId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "restaurant_id")
    private Restaurant restaurant;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "public_restaurant_id")
    private PublicRestaurant publicRestaurant;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id", nullable = false)
    private Account account;

    @Column(nullable = false)
    private byte rating;

    @Column(length = 1000)
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.ACTIVE;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    protected Review() {
    }

    private Review(Restaurant restaurant, PublicRestaurant publicRestaurant, Account account, byte rating, String content) {
        this.restaurant = restaurant;
        this.publicRestaurant = publicRestaurant;
        this.account = Objects.requireNonNull(account);
        this.rating = rating;
        this.content = content;
        this.status = Status.ACTIVE;
    }

    public static Review create(Restaurant restaurant, Account account, byte rating, String content) {
        return new Review(Objects.requireNonNull(restaurant), null, account, rating, content);
    }

    public static Review createForPublicRestaurant(PublicRestaurant publicRestaurant, Account account, byte rating, String content) {
        return new Review(null, Objects.requireNonNull(publicRestaurant), account, rating, content);
    }

    @PrePersist
    void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public Long getReviewId() {
        return reviewId;
    }

    public Restaurant getRestaurant() {
        return restaurant;
    }

    public PublicRestaurant getPublicRestaurant() {
        return publicRestaurant;
    }

    public Account getAccount() {
        return account;
    }

    public byte getRating() {
        return rating;
    }

    public String getContent() {
        return content;
    }

    public Status getStatus() {
        return status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public enum Status {
        ACTIVE,
        DELETED
    }
}
