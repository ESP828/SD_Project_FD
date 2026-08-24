package com.example.backend.restaurant.domain.entity;

import com.example.backend.restaurant.domain.type.RestaurantStatus;
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

    // 공공데이터 API는 중분류명을 안 내려줘서(항상 빈 값), 소분류 코드 기준으로 우리가
    // 정의한 대분류 그룹(한식/양식/중식/일식/아시안/카페·디저트/패스트푸드/분식/주점/구내식당·뷔페)을
    // 대신 채워 넣는다. update()에서 매번 다시 계산한다.
    @Column(name = "category_medium_name", length = 50)
    private String categoryMediumName;

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

    // 소프트 삭제 상태. JPA 엔티티에 매핑이 안 돼 있어서 추천/랭킹 후보 조회(JPQL)가
    // DELETED 매장까지 그대로 끌어오던 버그가 있었다 - 이제 이 필드로 걸러낼 수 있다.
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private RestaurantStatus status = RestaurantStatus.ACTIVE;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    // 매장 대표 이미지 URL. 공공데이터엔 사진이 없어서, 추천/랭킹 화면에 노출될 때
    // 카카오 이미지 검색으로 한 번 찾아서 캐싱해둔다. null = 아직 검색 안 해봄,
    // 빈 문자열 = 검색은 해봤는데 결과가 없었음(이 경우 재검색하지 않는다).
    @Column(name = "image_url", length = 500)
    private String imageUrl;

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
        this.categoryMediumName = PublicRestaurantCategoryGroup.resolve(categorySmallCode);
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

    public String getCategoryMediumName() {
        return categoryMediumName;
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

    public RestaurantStatus getStatus() {
        return status;
    }

    public boolean isActive() {
        return status == RestaurantStatus.ACTIVE;
    }

    public LocalDateTime getDeletedAt() {
        return deletedAt;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public void cacheImageUrl(String imageUrl) {
        this.imageUrl = imageUrl == null ? "" : imageUrl;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
