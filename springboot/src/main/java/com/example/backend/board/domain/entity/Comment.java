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
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

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
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Post post;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false)
    private Account author;

    @Column(name = "parent_comment_id")
    private Long parentCommentId;

    @Column(nullable = false, length = 1000)
    private String content;

    @Column(name = "image_mime_type", length = 100)
    private String imageMimeType;

    @Column(name = "image_original_name", length = 255)
    private String imageOriginalName;

    @Column(name = "image_file_size")
    private Long imageFileSize;

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

    private Comment(
            Post post,
            Account author,
            String content,
            Long parentCommentId
    ) {
        this.post = Objects.requireNonNull(post);
        this.author = Objects.requireNonNull(author);
        this.content = Objects.requireNonNull(content);
        this.parentCommentId = parentCommentId;
        this.status = CommentStatus.ACTIVE;
    }

    public static Comment create(Post post, Account author, String content) {
        return new Comment(post, author, content, null);
    }

    public static Comment createReply(
            Post post,
            Account author,
            String content,
            Long parentCommentId
    ) {
        if (parentCommentId == null || parentCommentId < 1) {
            throw new IllegalArgumentException("부모 댓글 번호가 필요합니다.");
        }
        return new Comment(post, author, content, parentCommentId);
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

    public Long getParentCommentId() {
        return parentCommentId;
    }

    public boolean isReply() {
        return parentCommentId != null;
    }

    public boolean hasImage() {
        return imageFileSize != null
                && imageFileSize > 0
                && imageMimeType != null
                && !imageMimeType.isBlank();
    }

    public String getImageMimeType() {
        return imageMimeType;
    }

    public String getImageOriginalName() {
        return imageOriginalName;
    }

    public Long getImageFileSize() {
        return imageFileSize;
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
