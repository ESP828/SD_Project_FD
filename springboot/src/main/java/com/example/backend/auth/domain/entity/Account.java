package com.example.backend.auth.domain.entity;

import com.example.backend.auth.domain.type.AccountStatus;
import com.example.backend.auth.domain.type.Gender;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Objects;

@Entity
@Table(name = "account")
public class Account {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "account_id")
    private Long accountId;

    @Column(name = "login_id", unique = true, length = 50)
    private String loginId;

    @Column(unique = true, length = 254)
    private String email;

    @Column(nullable = false, unique = true, length = 30)
    private String nickname;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Gender gender = Gender.UNSPECIFIED;

    @Column(name = "birth_date")
    private LocalDate birthDate;

    @Column(name = "profile_image_url", length = 500)
    private String profileImageUrl;

    @Column(name = "email_verified", nullable = false)
    private boolean emailVerified;

    @Column(name = "profile_completed", nullable = false)
    private boolean profileCompleted;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AccountStatus status = AccountStatus.ACTIVE;

    @Column(name = "last_login_at")
    private LocalDateTime lastLoginAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    protected Account() {
    }

    private Account(String loginId, String email, String nickname, boolean profileCompleted) {
        this.loginId = loginId;
        this.email = email;
        this.nickname = Objects.requireNonNull(nickname);
        this.profileCompleted = profileCompleted;
    }

    public static Account local(String loginId, String email, String nickname) {
        return new Account(
                Objects.requireNonNull(loginId),
                Objects.requireNonNull(email),
                Objects.requireNonNull(nickname),
                true
        );
    }

    public static Account social(String email, String nickname) {
        return new Account(null, email, Objects.requireNonNull(nickname), email != null);
    }

    public void markLoginSucceeded() {
        this.lastLoginAt = LocalDateTime.now();
    }

    public void updateProfile(String nickname, Gender gender, LocalDate birthDate) {
        this.nickname = Objects.requireNonNull(nickname);
        this.gender = Objects.requireNonNull(gender);
        this.birthDate = birthDate;
    }

    public boolean isActive() {
        return status == AccountStatus.ACTIVE;
    }

    public void activate() {
        this.status = AccountStatus.ACTIVE;
    }

    public void suspend() {
        this.status = AccountStatus.SUSPENDED;
    }

    public void withdraw() {
        this.status = AccountStatus.WITHDRAWN;
        this.deletedAt = LocalDateTime.now();
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

    public Long getAccountId() {
        return accountId;
    }

    public String getLoginId() {
        return loginId;
    }

    public String getEmail() {
        return email;
    }

    public String getNickname() {
        return nickname;
    }

    public Gender getGender() {
        return gender;
    }

    public LocalDate getBirthDate() {
        return birthDate;
    }

    public String getProfileImageUrl() {
        return profileImageUrl;
    }

    public boolean isEmailVerified() {
        return emailVerified;
    }

    public boolean isProfileCompleted() {
        return profileCompleted;
    }

    public AccountStatus getStatus() {
        return status;
    }

    public LocalDateTime getLastLoginAt() {
        return lastLoginAt;
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
