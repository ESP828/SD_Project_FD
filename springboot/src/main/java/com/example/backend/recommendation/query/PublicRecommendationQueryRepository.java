package com.example.backend.recommendation.query;

import com.example.backend.restaurant.domain.entity.PublicRestaurant;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PublicRecommendationQueryRepository extends JpaRepository<PublicRestaurant, Long> {

    // 1. 위도/경도 범위 검색
    @Query("""
        SELECT p FROM PublicRestaurant p
        WHERE (:minLat IS NULL OR p.latitude >= :minLat)
          AND (:maxLat IS NULL OR p.latitude <= :maxLat)
          AND (:minLng IS NULL OR p.longitude >= :minLng)
          AND (:maxLng IS NULL OR p.longitude <= :maxLng)
    """)
    List<PublicRestaurant> findCandidatesInBounds(
            @Param("minLat") Double minLat,
            @Param("maxLat") Double maxLat,
            @Param("minLng") Double minLng,
            @Param("maxLng") Double maxLng,
            Pageable pageable
    );

    /**
     * 2. 특정 사용자(accountId)가 찜한 공공 음식점 목록 조회
     * FavoriteId 자바 필드명(restaurantId)을 바라보도록 작성
     */
    @Query("""
        SELECT p FROM PublicRestaurant p
        JOIN Favorite f ON p.publicRestaurantId = f.id.restaurantId
        WHERE f.id.accountId = :accountId
    """)
    List<PublicRestaurant> findFavoritesByAccountId(@Param("accountId") Long accountId);

}
