package com.example.backend.auth.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.LocalDateTime;
import java.util.Objects;

@Entity
@Table(name = "account_authority")
public class AccountAuthority {

    @EmbeddedId
    private AccountAuthorityId id;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected AccountAuthority() {
    }

    public AccountAuthority(Long accountId, Short authorityId) {
        this.id = new AccountAuthorityId(accountId, authorityId);
    }

    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now();
    }

    public AccountAuthorityId getId() {
        return id;
    }

    public Long getAccountId() {
        return Objects.requireNonNull(id).getAccountId();
    }

    public Short getAuthorityId() {
        return Objects.requireNonNull(id).getAuthorityId();
    }
}
