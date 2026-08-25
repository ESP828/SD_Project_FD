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
        WHERE p.status = com.example.backend.restaurant.domain.type.RestaurantStatus.ACTIVE
          AND (:minLat IS NULL OR p.latitude >= :minLat)
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

    @Query("""
        SELECT p FROM PublicRestaurant p
        WHERE p.status = com.example.backend.restaurant.domain.type.RestaurantStatus.ACTIVE
          AND (:minLat IS NULL OR p.latitude >= :minLat)
          AND (:maxLat IS NULL OR p.latitude <= :maxLat)
          AND (:minLng IS NULL OR p.longitude >= :minLng)
          AND (:maxLng IS NULL OR p.longitude <= :maxLng)
          AND (:categoryMedium IS NULL OR p.categoryMediumName = :categoryMedium)
          AND (:categorySmallKeyword IS NULL
               OR p.categorySmallName LIKE CONCAT('%', :categorySmallKeyword, '%'))
        ORDER BY
          CASE WHEN :centerLat IS NULL OR :centerLng IS NULL THEN 0
               ELSE (p.latitude - :centerLat) * (p.latitude - :centerLat)
                  + (p.longitude - :centerLng) * (p.longitude - :centerLng)
          END ASC
    """)
    List<PublicRestaurant> findCandidatesInBoundsWithCategory(
            @Param("minLat") Double minLat,
            @Param("maxLat") Double maxLat,
            @Param("minLng") Double minLng,
            @Param("maxLng") Double maxLng,
            @Param("centerLat") Double centerLat,
            @Param("centerLng") Double centerLng,
            @Param("categoryMedium") String categoryMedium,
            @Param("categorySmallKeyword") String categorySmallKeyword,
            Pageable pageable
    );

    /**
     * 개인화 후보. 상한은 반경 안의 매장을 다 담을 만큼 크게 잡으므로 평소에는 절단이 일어나지 않고,
     * 취향 점수는 반경 안 전체를 대상으로 계산된다.
     *
     * <p>정렬은 상한이 실제로 걸릴 때만 의미가 있다. 그때 버려야 할 것은 "가장 먼 매장"이지
     * "ID가 큰 매장"이 아니다. ID 순으로 자르면 반경 안 매장의 대부분이 취향 평가를 받지 못한 채
     * 사라진다(강남 3km 기준 13,463건 중 830건만 평가되던 원인). 중심 좌표는 요청마다 고정이므로
     * 거리 순으로 정렬해도 같은 요청은 같은 후보군을 본다.
     */
    @Query("""
        SELECT p FROM PublicRestaurant p
        WHERE p.status = com.example.backend.restaurant.domain.type.RestaurantStatus.ACTIVE
          AND (:minLat IS NULL OR p.latitude >= :minLat)
          AND (:maxLat IS NULL OR p.latitude <= :maxLat)
          AND (:minLng IS NULL OR p.longitude >= :minLng)
          AND (:maxLng IS NULL OR p.longitude <= :maxLng)
        ORDER BY
          CASE WHEN :centerLat IS NULL OR :centerLng IS NULL THEN 0
               ELSE (p.latitude - :centerLat) * (p.latitude - :centerLat)
                  + (p.longitude - :centerLng) * (p.longitude - :centerLng)
          END ASC,
          p.publicRestaurantId ASC
    """)
    List<PublicRestaurant> findPersonalCandidatesInBounds(
            @Param("minLat") Double minLat,
            @Param("maxLat") Double maxLat,
            @Param("minLng") Double minLng,
            @Param("maxLng") Double maxLng,
            @Param("centerLat") Double centerLat,
            @Param("centerLng") Double centerLng,
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
          AND p.status = com.example.backend.restaurant.domain.type.RestaurantStatus.ACTIVE
    """)
    List<PublicRestaurant> findFavoritesByAccountId(@Param("accountId") Long accountId);

}
