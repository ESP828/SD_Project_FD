package com.example.backend.restaurant.domain.entity;

import com.example.backend.auth.domain.entity.Account;
import com.example.backend.restaurant.domain.type.RestaurantStatus;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Objects;

@Entity
@Table(name = "restaurant")
public class Restaurant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "restaurant_id")
    private Long restaurantId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_account_id", nullable = false)
    private Account owner;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private RestaurantCategory category;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, length = 255)
    private String address;

    @Column(name = "address_detail", length = 255)
    private String addressDetail;

    @Column(precision = 10, scale = 7)
    private BigDecimal latitude;

    @Column(precision = 10, scale = 7)
    private BigDecimal longitude;

    @Column(length = 30)
    private String phone;

    @Column(name = "opening_hours", length = 500)
    private String openingHours;

    @Column(name = "closed_days", length = 255)
    private String closedDays;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RestaurantStatus status = RestaurantStatus.ACTIVE;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    protected Restaurant() {
    }

    private Restaurant(
            Account owner,
            RestaurantCategory category,
            String name,
            String address,
            String addressDetail,
            BigDecimal latitude,
            BigDecimal longitude,
            String phone,
            String openingHours,
            String closedDays,
            String description
    ) {
        this.owner = Objects.requireNonNull(owner);
        this.category = category;
        this.name = Objects.requireNonNull(name);
        this.address = Objects.requireNonNull(address);
        this.addressDetail = addressDetail;
        this.latitude = latitude;
        this.longitude = longitude;
        this.phone = phone;
        this.openingHours = openingHours;
        this.closedDays = closedDays;
        this.description = description;
        this.status = RestaurantStatus.ACTIVE;
    }

    public static Restaurant create(
            Account owner,
            RestaurantCategory category,
            String name,
            String address,
            String addressDetail,
            BigDecimal latitude,
            BigDecimal longitude,
            String phone,
            String openingHours,
            String closedDays,
            String description
    ) {
        return new Restaurant(
                owner,
                category,
                name,
                address,
                addressDetail,
                latitude,
                longitude,
                phone,
                openingHours,
                closedDays,
                description
        );
    }

    public void update(
            RestaurantCategory category,
            String name,
            String address,
            String addressDetail,
            BigDecimal latitude,
            BigDecimal longitude,
            String phone,
            String openingHours,
            String closedDays,
            String description
    ) {
        this.category = category;
        this.name = name;
        this.address = address;
        this.addressDetail = addressDetail;
        this.latitude = latitude;
        this.longitude = longitude;
        this.phone = phone;
        this.openingHours = openingHours;
        this.closedDays = closedDays;
        this.description = description;
    }

    public void delete() {
        this.status = RestaurantStatus.DELETED;
        this.deletedAt = LocalDateTime.now();
    }

    public void activate() {
        this.status = RestaurantStatus.ACTIVE;
    }

    public void deactivate() {
        this.status = RestaurantStatus.INACTIVE;
    }

    public boolean isActive() {
        return status == RestaurantStatus.ACTIVE;
    }

    public boolean isDeleted() {
        return status == RestaurantStatus.DELETED;
    }

    public boolean isReadableBy(Long viewerAccountId) {
        return isActive()
                || (status == RestaurantStatus.INACTIVE
                && viewerAccountId != null
                && Objects.equals(owner.getAccountId(), viewerAccountId));
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

    public Long getRestaurantId() {
        return restaurantId;
    }

    public Account getOwner() {
        return owner;
    }

    public RestaurantCategory getCategory() {
        return category;
    }

    public String getName() {
        return name;
    }

    public String getAddress() {
        return address;
    }

    public String getAddressDetail() {
        return addressDetail;
    }

    public BigDecimal getLatitude() {
        return latitude;
    }

    public BigDecimal getLongitude() {
        return longitude;
    }

    public String getPhone() {
        return phone;
    }

    public String getOpeningHours() {
        return openingHours;
    }

    public String getClosedDays() {
        return closedDays;
    }

    public String getDescription() {
        return description;
    }

    public RestaurantStatus getStatus() {
        return status;
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
