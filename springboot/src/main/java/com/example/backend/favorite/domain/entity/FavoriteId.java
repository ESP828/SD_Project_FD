package com.example.backend.favorite.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.io.Serializable;
import java.util.Objects;

@Embeddable
public class FavoriteId implements Serializable {

    @Column(name = "account_id")
    private Long accountId;

    @Column(name = "restaurant_id")
    private Long restaurantId;

    protected FavoriteId() {
    }

    public FavoriteId(Long accountId, Long restaurantId) {
        this.accountId = Objects.requireNonNull(accountId);
        this.restaurantId = Objects.requireNonNull(restaurantId);
    }

    public Long getAccountId() {
        return accountId;
    }

    public Long getRestaurantId() {
        return restaurantId;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (!(object instanceof FavoriteId that)) {
            return false;
        }
        return Objects.equals(accountId, that.accountId)
                && Objects.equals(restaurantId, that.restaurantId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(accountId, restaurantId);
    }
}
