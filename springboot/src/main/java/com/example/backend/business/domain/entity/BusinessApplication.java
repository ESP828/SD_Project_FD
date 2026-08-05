package com.example.backend.business.domain.entity;

import com.example.backend.auth.domain.entity.Account;
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
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "business_application")
public class BusinessApplication {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "application_id")
    private Long applicationId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id", nullable = false)
    private Account account;

    @Column(name = "business_name", nullable = false, length = 100)
    private String businessName;

    @Column(name = "business_number", nullable = false, length = 20)
    private String businessNumber;

    @Column(name = "representative_name", nullable = false, length = 50)
    private String representativeName;

    @Column(name = "opened_at")
    private LocalDate openedAt;

    @Column(nullable = false, length = 30)
    private String contact;

    @Column(length = 500)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.PENDING;

    @Column(name = "reject_reason", length = 500)
    private String rejectReason;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "processed_by")
    private Account processedBy;

    @Column(name = "processed_at")
    private LocalDateTime processedAt;

    @Column(name = "canceled_at")
    private LocalDateTime canceledAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected BusinessApplication() {
    }

    public BusinessApplication(Account account, String businessName, String businessNumber,
                              String representativeName, LocalDate openedAt, String contact, String reason) {
        this.account = account;
        this.businessName = businessName;
        this.businessNumber = businessNumber;
        this.representativeName = representativeName;
        this.openedAt = openedAt;
        this.contact = contact;
        this.reason = reason;
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

    public void approve(Account processedBy) {
        requirePending();
        this.status = Status.APPROVED;
        this.processedBy = processedBy;
        this.processedAt = LocalDateTime.now();
        this.rejectReason = null;
    }

    public void reject(Account processedBy, String rejectReason) {
        requirePending();
        if (rejectReason == null || rejectReason.isBlank()) {
            throw new IllegalArgumentException("거절 사유는 필수입니다.");
        }
        this.status = Status.REJECTED;
        this.processedBy = processedBy;
        this.processedAt = LocalDateTime.now();
        this.rejectReason = rejectReason;
    }

    private void requirePending() {
        if (status != Status.PENDING) {
            throw new IllegalStateException("대기 중인 신청만 처리할 수 있습니다.");
        }
    }

    public Long getApplicationId() {
        return applicationId;
    }

    public Account getAccount() {
        return account;
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

    public LocalDate getOpenedAt() {
        return openedAt;
    }

    public String getContact() {
        return contact;
    }

    public String getReason() {
        return reason;
    }

    public Status getStatus() {
        return status;
    }

    public String getRejectReason() {
        return rejectReason;
    }

    public Account getProcessedBy() {
        return processedBy;
    }

    public LocalDateTime getProcessedAt() {
        return processedAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public enum Status {
        PENDING,
        APPROVED,
        REJECTED,
        CANCELED
    }
}
