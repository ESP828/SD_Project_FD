package com.example.backend.auth.domain.entity;

import com.example.backend.auth.domain.type.SocialProvider;
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
import jakarta.persistence.UniqueConstraint;

import java.time.LocalDateTime;
import java.util.Objects;

@Entity
@Table(
        name = "social_account",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_social_provider_user", columnNames = {"provider", "provider_user_id"}),
                @UniqueConstraint(name = "uk_social_account_provider", columnNames = {"account_id", "provider"})
        }
)
public class SocialAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "social_account_id")
    private Long socialAccountId;

    @Column(name = "account_id", nullable = false)
    private Long accountId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SocialProvider provider;

    @Column(name = "provider_user_id", nullable = false, length = 191)
    private String providerUserId;

    @Column(name = "provider_email", length = 254)
    private String providerEmail;

    @Column(name = "provider_nickname", length = 100)
    private String providerNickname;

    @Column(name = "connected_at", nullable = false, updatable = false)
    private LocalDateTime connectedAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected SocialAccount() {
    }

    public SocialAccount(
            Long accountId,
            SocialProvider provider,
            String providerUserId,
            String providerEmail,
            String providerNickname
    ) {
        this.accountId = Objects.requireNonNull(accountId);
        this.provider = Objects.requireNonNull(provider);
        this.providerUserId = Objects.requireNonNull(providerUserId);
        this.providerEmail = providerEmail;
        this.providerNickname = providerNickname;
    }

    public void updateSnapshot(String email, String nickname) {
        providerEmail = email;
        providerNickname = nickname;
    }

    @PrePersist
    void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        connectedAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public Long getSocialAccountId() {
        return socialAccountId;
    }

    public Long getAccountId() {
        return accountId;
    }

    public SocialProvider getProvider() {
        return provider;
    }

    public String getProviderUserId() {
        return providerUserId;
    }

    public String getProviderEmail() {
        return providerEmail;
    }

    public String getProviderNickname() {
        return providerNickname;
    }
}
