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

    // 1. 위도/경도 범위 검색 (중심좌표가 있으면 가까운 순으로 정렬 후 상위 N건만 사용한다.
    //    정렬이 없으면 밀집 지역에서 Pageable의 limit이 "가까운 N건"이 아니라 임의의 N건을
    //    잘라내게 되어 실제로 반경 안에 있는 매장이 후보에서 통째로 누락될 수 있다.)
    @Query("""
        SELECT p FROM PublicRestaurant p
        WHERE (:minLat IS NULL OR p.latitude >= :minLat)
          AND (:maxLat IS NULL OR p.latitude <= :maxLat)
          AND (:minLng IS NULL OR p.longitude >= :minLng)
          AND (:maxLng IS NULL OR p.longitude <= :maxLng)
        ORDER BY
          CASE WHEN :centerLat IS NULL OR :centerLng IS NULL THEN 0
               ELSE (p.latitude - :centerLat) * (p.latitude - :centerLat)
                  + (p.longitude - :centerLng) * (p.longitude - :centerLng)
          END ASC
    """)
    List<PublicRestaurant> findCandidatesInBounds(
            @Param("minLat") Double minLat,
            @Param("maxLat") Double maxLat,
            @Param("minLng") Double minLng,
            @Param("maxLng") Double maxLng,
            @Param("centerLat") Double centerLat,
            @Param("centerLng") Double centerLng,
            Pageable pageable
    );

    /**
     * 1-1. 반경 안에서 "실제 리뷰가 있는" 매장만 거리 제한 없이 전부 조회한다.
     * findCandidatesInBounds는 가까운 순 N건만 자르는데, 밀집 지역(예: 강남)은 반경 10km
     * 안에만 수만 개 매장이 있어 그 N건이 실질적으로 반경을 수백 m로 줄여버린다. 리뷰가
     * 있는 매장은 DB 전체에서도 수가 적어(수십~수백 건) 이 쿼리로 별도 조회해 후보에
     * 합쳐도 부담이 없고, 이렇게 해야 "AI 감성분석 긍정비율" 랭킹이 실제 리뷰 데이터를
     * 반영할 수 있다.
     */
    @Query("""
        SELECT DISTINCT p FROM PublicRestaurant p
        JOIN Review r ON r.publicRestaurant.publicRestaurantId = p.publicRestaurantId
        WHERE r.status = 'ACTIVE'
          AND (:minLat IS NULL OR p.latitude >= :minLat)
          AND (:maxLat IS NULL OR p.latitude <= :maxLat)
          AND (:minLng IS NULL OR p.longitude >= :minLng)
          AND (:maxLng IS NULL OR p.longitude <= :maxLng)
    """)
    List<PublicRestaurant> findRestaurantsWithActiveReviewsInBounds(
            @Param("minLat") Double minLat,
            @Param("maxLat") Double maxLat,
            @Param("minLng") Double minLng,
            @Param("maxLng") Double maxLng
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
