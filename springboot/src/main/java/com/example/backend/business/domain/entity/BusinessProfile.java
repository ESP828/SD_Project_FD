package com.example.backend.business.domain.entity;

import com.example.backend.auth.domain.entity.Account;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.LocalDateTime;
import java.util.Objects;

@Entity
@Table(name = "business_profile")
public class BusinessProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "business_profile_id")
    private Long businessProfileId;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false, unique = true)
    private Account account;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "application_id")
    private BusinessApplication application;

    @Column(name = "business_name", nullable = false, length = 100)
    private String businessName;

    @Column(name = "business_number", nullable = false, unique = true, length = 20)
    private String businessNumber;

    @Column(name = "representative_name", nullable = false, length = 50)
    private String representativeName;

    @Column(nullable = false, length = 30)
    private String contact;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected BusinessProfile() {
    }

    public BusinessProfile(BusinessApplication application) {
        this.account = Objects.requireNonNull(application).getAccount();
        updateFrom(application);
    }

    public void updateFrom(BusinessApplication application) {
        BusinessApplication source = Objects.requireNonNull(application);
        if (!account.getAccountId().equals(source.getAccount().getAccountId())) {
            throw new IllegalArgumentException("다른 계정의 사업자 신청으로 프로필을 변경할 수 없습니다.");
        }
        this.application = source;
        this.businessName = source.getBusinessName();
        this.businessNumber = source.getBusinessNumber();
        this.representativeName = source.getRepresentativeName();
        this.contact = source.getContact();
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

    public Long getBusinessProfileId() {
        return businessProfileId;
    }

    public Account getAccount() {
        return account;
    }

    public BusinessApplication getApplication() {
        return application;
    }

    public String getBusinessName() {
        return businessName;
    }

    public String getBusinessNumber() {
        return businessNumber;
    }

    public String getRepresentativeName() {
        return representativeName;
    }

    public String getContact() {
        return contact;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
