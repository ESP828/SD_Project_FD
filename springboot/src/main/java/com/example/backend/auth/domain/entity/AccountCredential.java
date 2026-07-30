package com.example.backend.auth.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.LocalDateTime;
import java.util.Objects;

@Entity
@Table(name = "account_credential")
public class AccountCredential {

    @Id
    @Column(name = "account_id")
    private Long accountId;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "failed_login_count", nullable = false)
    private short failedLoginCount;

    @Column(name = "locked_until")
    private LocalDateTime lockedUntil;

    @Column(name = "password_changed_at", nullable = false)
    private LocalDateTime passwordChangedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected AccountCredential() {
    }

    public AccountCredential(Long accountId, String passwordHash) {
        this.accountId = Objects.requireNonNull(accountId);
        this.passwordHash = Objects.requireNonNull(passwordHash);
    }

    @PrePersist
    void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        passwordChangedAt = now;
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public Long getAccountId() {
        return accountId;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public short getFailedLoginCount() {
        return failedLoginCount;
    }

    public LocalDateTime getLockedUntil() {
        return lockedUntil;
    }
}
