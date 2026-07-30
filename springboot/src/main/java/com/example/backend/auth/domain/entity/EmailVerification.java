package com.example.backend.auth.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.LocalDateTime;
import java.util.Objects;

@Entity
@Table(
        name = "signup_email_verification",
        uniqueConstraints = @UniqueConstraint(name = "uk_signup_email_verification_email", columnNames = "email")
)
public class EmailVerification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "email_verification_id")
    private Long emailVerificationId;

    @Column(nullable = false, length = 254)
    private String email;

    @Column(nullable = false, length = 6)
    private String code;

    @Column(nullable = false)
    private boolean verified;

    @Column(name = "attempt_count", nullable = false)
    private short attemptCount;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected EmailVerification() {
    }

    public EmailVerification(String email, String code, LocalDateTime expiresAt) {
        this.email = Objects.requireNonNull(email);
        this.code = Objects.requireNonNull(code);
        this.expiresAt = Objects.requireNonNull(expiresAt);
        this.verified = false;
        this.attemptCount = 0;
    }

    public void reissue(String code, LocalDateTime expiresAt) {
        this.code = Objects.requireNonNull(code);
        this.expiresAt = Objects.requireNonNull(expiresAt);
        this.verified = false;
        this.attemptCount = 0;
    }

    public boolean isExpired() {
        return LocalDateTime.now().isAfter(expiresAt);
    }

    public void registerFailedAttempt() {
        attemptCount++;
    }

    public void markVerified(LocalDateTime expiresAt) {
        this.verified = true;
        this.expiresAt = Objects.requireNonNull(expiresAt);
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

    public Long getEmailVerificationId() {
        return emailVerificationId;
    }

    public String getEmail() {
        return email;
    }

    public String getCode() {
        return code;
    }

    public boolean isVerified() {
        return verified;
    }

    public short getAttemptCount() {
        return attemptCount;
    }

    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }
}
