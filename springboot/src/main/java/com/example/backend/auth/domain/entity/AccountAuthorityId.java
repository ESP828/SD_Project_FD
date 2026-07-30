package com.example.backend.auth.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.io.Serializable;
import java.util.Objects;

@Embeddable
public class AccountAuthorityId implements Serializable {

    @Column(name = "account_id")
    private Long accountId;

    @Column(name = "authority_id")
    private Short authorityId;

    protected AccountAuthorityId() {
    }

    public AccountAuthorityId(Long accountId, Short authorityId) {
        this.accountId = Objects.requireNonNull(accountId);
        this.authorityId = Objects.requireNonNull(authorityId);
    }

    public Long getAccountId() {
        return accountId;
    }

    public Short getAuthorityId() {
        return authorityId;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (!(object instanceof AccountAuthorityId that)) {
            return false;
        }
        return Objects.equals(accountId, that.accountId)
                && Objects.equals(authorityId, that.authorityId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(accountId, authorityId);
    }
}
