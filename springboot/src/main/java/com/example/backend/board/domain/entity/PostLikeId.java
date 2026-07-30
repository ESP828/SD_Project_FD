package com.example.backend.board.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.io.Serializable;
import java.util.Objects;

@Embeddable
public class PostLikeId implements Serializable {

    @Column(name = "post_id")
    private Long postId;

    @Column(name = "account_id")
    private Long accountId;

    protected PostLikeId() {
    }

    public PostLikeId(Long postId, Long accountId) {
        this.postId = postId;
        this.accountId = accountId;
    }

    public Long getPostId() {
        return postId;
    }

    public Long getAccountId() {
        return accountId;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof PostLikeId that)) {
            return false;
        }
        return Objects.equals(postId, that.postId)
                && Objects.equals(accountId, that.accountId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(postId, accountId);
    }
}
