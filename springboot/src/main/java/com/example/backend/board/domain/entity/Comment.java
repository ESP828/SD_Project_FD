package com.example.backend.board.domain.entity;

import com.example.backend.auth.domain.entity.Account;
import com.example.backend.board.domain.type.CommentStatus;
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
import jakarta.persistence.Table;

import java.time.LocalDateTime;
import java.util.Objects;

@Entity
@Table(name = "post_comment")
public class Comment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "comment_id")
    private Long commentId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "post_id", nullable = false)
    private Post post;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false)
    private Account author;

    @Column(nullable = false, length = 1000)
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CommentStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    protected Comment() {
    }

    private Comment(Post post, Account author, String content) {
        this.post = Objects.requireNonNull(post);
        this.author = Objects.requireNonNull(author);
        this.content = Objects.requireNonNull(content);
        this.status = CommentStatus.ACTIVE;
    }

    public static Comment create(Post post, Account author, String content) {
        return new Comment(post, author, content);
    }

    public void update(String content) {
        this.content = Objects.requireNonNull(content);
        this.updatedAt = LocalDateTime.now();
    }

    public void softDelete(LocalDateTime deletedAt) {
        this.status = CommentStatus.DELETED;
        this.deletedAt = Objects.requireNonNull(deletedAt);
        this.updatedAt = deletedAt;
    }

    public boolean isDeleted() {
        return status == CommentStatus.DELETED;
    }

    @PrePersist
    void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        if (status == null) {
            status = CommentStatus.ACTIVE;
        }
        createdAt = now;
        updatedAt = now;
    }

    public Long getCommentId() {
        return commentId;
    }

    public Post getPost() {
        return post;
    }

    public Account getAuthor() {
        return author;
    }

    public String getContent() {
        return content;
    }

    public CommentStatus getStatus() {
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
