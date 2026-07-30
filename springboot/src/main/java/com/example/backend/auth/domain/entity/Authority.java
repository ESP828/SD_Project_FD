package com.example.backend.auth.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.LocalDateTime;
import java.util.Objects;

@Entity
@Table(name = "authority")
public class Authority {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "authority_id")
    private Short authorityId;

    @Column(name = "authority_code", nullable = false, unique = true, length = 30)
    private String authorityCode;

    @Column(name = "authority_name", nullable = false, length = 50)
    private String authorityName;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected Authority() {
    }

    public Authority(String authorityCode, String authorityName) {
        this.authorityCode = Objects.requireNonNull(authorityCode);
        this.authorityName = Objects.requireNonNull(authorityName);
    }

    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now();
    }

    public Short getAuthorityId() {
        return authorityId;
    }

    public String getAuthorityCode() {
        return authorityCode;
    }

    public String getAuthorityName() {
        return authorityName;
    }
}
