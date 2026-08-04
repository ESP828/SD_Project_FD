package com.example.backend.restaurant.repository;

import com.example.backend.restaurant.domain.entity.PublicRestaurant;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface PublicRestaurantRepository extends JpaRepository<PublicRestaurant, Long>, JpaSpecificationExecutor<PublicRestaurant> {

    Optional<PublicRestaurant> findByExternalStoreId(String externalStoreId);

    List<PublicRestaurant> findByLatitudeBetweenAndLongitudeBetween(
            BigDecimal minLatitude,
            BigDecimal maxLatitude,
            BigDecimal minLongitude,
            BigDecimal maxLongitude,
            Pageable pageable
    );

    /**
     * ngram 파서 기반 FULLTEXT 인덱스(ft_public_restaurant_search)로 상호명·카테고리·주소를
     * 함께 검색하고, MySQL이 계산하는 관련성 점수(MATCH ... AGAINST) 순으로 정렬한다.
     */
    @Query(value = """
            SELECT p.* FROM public_restaurant p
            WHERE p.latitude BETWEEN :minLatitude AND :maxLatitude
              AND p.longitude BETWEEN :minLongitude AND :maxLongitude
              AND MATCH(p.name, p.category_large_name, p.category_medium_name, p.category_small_name, p.road_address, p.lot_address)
                  AGAINST(:keyword IN NATURAL LANGUAGE MODE)
            ORDER BY MATCH(p.name, p.category_large_name, p.category_medium_name, p.category_small_name, p.road_address, p.lot_address)
                     AGAINST(:keyword IN NATURAL LANGUAGE MODE) DESC
            LIMIT :limit
            """, nativeQuery = true)
    List<PublicRestaurant> searchInBoundsByRelevance(
            @Param("minLatitude") BigDecimal minLatitude,
            @Param("maxLatitude") BigDecimal maxLatitude,
            @Param("minLongitude") BigDecimal minLongitude,
            @Param("maxLongitude") BigDecimal maxLongitude,
            @Param("keyword") String keyword,
            @Param("limit") int limit
    );

    long countByDataYm(String dataYm);
}
