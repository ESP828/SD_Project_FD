package com.example.backend.board.domain.entity;

import com.example.backend.auth.domain.entity.Account;
import com.example.backend.board.domain.type.BoardType;
import com.example.backend.board.domain.type.PostCategory;
import com.example.backend.board.domain.type.PostStatus;
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
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Entity
@Table(name = "post")
public class Post {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "post_id")
    private Long postId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false)
    private Account author;

    @Column(name = "restaurant_id")
    private Long restaurantId;

    @OneToMany(mappedBy = "post", fetch = FetchType.LAZY)
    private List<Comment> comments = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    @Column(name = "board_type", nullable = false, length = 20)
    private BoardType boardType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private PostCategory category;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "like_count", nullable = false)
    private int likeCount;

    @Column(name = "view_count", nullable = false)
    private int viewCount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PostStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    protected Post() {
    }

    private Post(
            Account author,
            Long restaurantId,
            BoardType boardType,
            PostCategory category,
            String title,
            String content
    ) {
        this.author = Objects.requireNonNull(author);
        this.restaurantId = restaurantId;
        this.boardType = Objects.requireNonNull(boardType);
        this.category = Objects.requireNonNull(category);
        this.title = Objects.requireNonNull(title);
        this.content = Objects.requireNonNull(content);
        this.status = PostStatus.ACTIVE;
    }

    public static Post create(
            Account author,
            Long restaurantId,
            BoardType boardType,
            PostCategory category,
            String title,
            String content
    ) {
        return new Post(author, restaurantId, boardType, category, title, content);
    }

    public void update(
            Long restaurantId,
            BoardType boardType,
            PostCategory category,
            String title,
            String content
    ) {
        this.restaurantId = restaurantId;
        this.boardType = Objects.requireNonNull(boardType);
        this.category = Objects.requireNonNull(category);
        this.title = Objects.requireNonNull(title);
        this.content = Objects.requireNonNull(content);
        this.updatedAt = LocalDateTime.now();
    }

    public void increaseLikeCount() {
        likeCount++;
    }

    public void decreaseLikeCount() {
        if (likeCount > 0) {
            likeCount--;
        }
    }

    public void clearLikeCount() {
        likeCount = 0;
    }

    public void softDelete(LocalDateTime deletedAt) {
        this.status = PostStatus.DELETED;
        this.deletedAt = Objects.requireNonNull(deletedAt);
        this.updatedAt = deletedAt;
    }

    public boolean isDeleted() {
        return status == PostStatus.DELETED;
    }

    @PrePersist
    void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        if (status == null) {
            status = PostStatus.ACTIVE;
        }
        createdAt = now;
        updatedAt = now;
    }

    public Long getPostId() {
        return postId;
    }

    public Account getAuthor() {
        return author;
    }

    public Long getRestaurantId() {
        return restaurantId;
    }

    public BoardType getBoardType() {
        return boardType;
    }

    public PostCategory getCategory() {
        return category;
    }

    public String getTitle() {
        return title;
    }

    public String getContent() {
        return content;
    }

    public long getLikeCount() {
        return likeCount;
    }

    public long getViewCount() {
        return viewCount;
    }

    public PostStatus getStatus() {
        return status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public LocalDateTime getDeletedAt() {
        return deletedAt;
    }
}
