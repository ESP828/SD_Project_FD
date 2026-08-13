package com.example.backend.restaurant.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 공공데이터포털 상가업소정보 API(소상공인시장진흥공단)로 적재한 음식점 데이터.
 * 사장님이 직접 등록하는 {@link Restaurant}와는 별개 테이블이다.
 */
@Entity
@Table(name = "public_restaurant")
public class PublicRestaurant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "public_restaurant_id")
    private Long publicRestaurantId;

    @Column(name = "external_store_id", nullable = false, unique = true, length = 30)
    private String externalStoreId;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(name = "branch_name", length = 100)
    private String branchName;

    @Column(name = "category_large_code", length = 10)
    private String categoryLargeCode;

    @Column(name = "category_large_name", length = 50)
    private String categoryLargeName;

    @Column(name = "category_medium_code", length = 10)
    private String categoryMediumCode;

    @Column(name = "category_small_code", length = 10)
    private String categorySmallCode;

    @Column(name = "category_small_name", length = 50)
    private String categorySmallName;

    @Column(name = "sido_name", length = 30)
    private String sidoName;

    @Column(name = "sigungu_name", length = 30)
    private String sigunguName;

    @Column(name = "road_address", length = 255)
    private String roadAddress;

    @Column(name = "lot_address", length = 255)
    private String lotAddress;

    @Column(precision = 10, scale = 7)
    private BigDecimal latitude;

    @Column(precision = 10, scale = 7)
    private BigDecimal longitude;

    @Column(name = "data_ym", length = 6)
    private String dataYm;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected PublicRestaurant() {
    }

    public PublicRestaurant(String externalStoreId, String name) {
        this.externalStoreId = Objects.requireNonNull(externalStoreId);
        this.name = Objects.requireNonNull(name);
    }

    public void update(
            String name,
            String branchName,
            String categoryLargeCode,
            String categoryLargeName,
            String categoryMediumCode,
            String categorySmallCode,
            String categorySmallName,
            String sidoName,
            String sigunguName,
            String roadAddress,
            String lotAddress,
            BigDecimal latitude,
            BigDecimal longitude,
            String dataYm
    ) {
        this.name = Objects.requireNonNull(name);
        this.branchName = branchName;
        this.categoryLargeCode = categoryLargeCode;
        this.categoryLargeName = categoryLargeName;
        this.categoryMediumCode = categoryMediumCode;
        this.categorySmallCode = categorySmallCode;
        this.categorySmallName = categorySmallName;
        this.sidoName = sidoName;
        this.sigunguName = sigunguName;
        this.roadAddress = roadAddress;
        this.lotAddress = lotAddress;
        this.latitude = latitude;
        this.longitude = longitude;
        this.dataYm = dataYm;
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

    public Long getPublicRestaurantId() {
        return publicRestaurantId;
    }

    public String getExternalStoreId() {
        return externalStoreId;
    }

    public String getName() {
        return name;
    }

    public String getBranchName() {
        return branchName;
    }

    public String getCategoryLargeCode() {
        return categoryLargeCode;
    }

    public String getCategoryLargeName() {
        return categoryLargeName;
    }

    public String getCategoryMediumCode() {
        return categoryMediumCode;
    }

    public String getCategorySmallCode() {
        return categorySmallCode;
    }

    public String getCategorySmallName() {
        return categorySmallName;
    }

    public String getSidoName() {
        return sidoName;
    }

    public String getSigunguName() {
        return sigunguName;
    }

    public String getRoadAddress() {
        return roadAddress;
    }

    public String getLotAddress() {
        return lotAddress;
    }

    public BigDecimal getLatitude() {
        return latitude;
    }

    public BigDecimal getLongitude() {
        return longitude;
    }

    public String getDataYm() {
        return dataYm;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
