package com.example.backend.board.domain.entity;

import com.example.backend.auth.domain.entity.Account;
import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.LocalDateTime;
import java.util.Objects;

@Entity
@Table(name = "post_like")
public class PostLike {

    @EmbeddedId
    private PostLikeId id;

    @MapsId("postId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "post_id", nullable = false)
    private Post post;

    @MapsId("accountId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false)
    private Account account;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected PostLike() {
    }

    private PostLike(Post post, Account account) {
        this.post = Objects.requireNonNull(post);
        this.account = Objects.requireNonNull(account);
        this.id = new PostLikeId(post.getPostId(), account.getAccountId());
    }

    public static PostLike create(Post post, Account account) {
        return new PostLike(post, account);
    }

    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now();
    }

    public PostLikeId getId() {
        return id;
    }

    public Post getPost() {
        return post;
    }

    public Account getAccount() {
        return account;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
