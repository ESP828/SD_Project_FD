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

    // 1. 위도/경도 범위 및 상호명/카테고리 유사 조건 검색
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
}
