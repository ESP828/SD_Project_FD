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
    List<PublicRestaurant> findTop10By();
    List<PublicRestaurant> findByName(String name);
    List<PublicRestaurant> findByLatitudeBetweenAndLongitudeBetween(
            BigDecimal minLatitude,
            BigDecimal maxLatitude,
            BigDecimal minLongitude,
            BigDecimal maxLongitude,
            Pageable pageable
    );

    /**
     * 상호명·카테고리·주소에 검색어가 정확히 포함된 매장만 찾는다(부분/오타 허용 없는 정확 매칭).
     * 상호명에 그대로 포함되는 매장을 최우선으로, 그 다음은 이름 길이가 짧은(더 일치도가 높은) 순.
     * ngram 풀텍스트 검색은 한글 특성상 "신논현역"처럼 흔한 부분 문자열이 겹치는 매장을 전부
     * 끌어와 정확한 상호명을 검색해도 관계없는 매장이 잔뜩 나오는 문제가 있어, 이 방식을 우선 사용한다.
     */
    @Query(value = """
            SELECT p.* FROM public_restaurant p
            WHERE p.latitude BETWEEN :minLatitude AND :maxLatitude
              AND p.longitude BETWEEN :minLongitude AND :maxLongitude
              AND (
                p.name LIKE :keywordPattern
                OR p.category_large_name LIKE :keywordPattern
                OR p.category_small_name LIKE :keywordPattern
                OR p.road_address LIKE :keywordPattern
                OR p.lot_address LIKE :keywordPattern
              )
            ORDER BY CASE WHEN p.name LIKE :keywordPattern THEN 0 ELSE 1 END, CHAR_LENGTH(p.name) ASC
            LIMIT :limit
            """, nativeQuery = true)
    List<PublicRestaurant> searchInBoundsByExactContains(
            @Param("minLatitude") BigDecimal minLatitude,
            @Param("maxLatitude") BigDecimal maxLatitude,
            @Param("minLongitude") BigDecimal minLongitude,
            @Param("maxLongitude") BigDecimal maxLongitude,
            @Param("keywordPattern") String keywordPattern,
            @Param("limit") int limit
    );

    /**
     * ngram 파서 기반 FULLTEXT 인덱스(ft_public_restaurant_search)로 상호명·카테고리·주소를
     * 함께 검색하고, MySQL이 계산하는 관련성 점수(MATCH ... AGAINST) 순으로 정렬한다.
     * 정확 매칭({@link #searchInBoundsByExactContains})에서 결과가 없을 때의 폴백으로 사용한다.
     */
    @Query(value = """
            SELECT p.* FROM public_restaurant p
            WHERE p.latitude BETWEEN :minLatitude AND :maxLatitude
              AND p.longitude BETWEEN :minLongitude AND :maxLongitude
              AND MATCH(p.name, p.category_large_name, p.category_small_name, p.road_address, p.lot_address)
                  AGAINST(:keyword IN NATURAL LANGUAGE MODE)
            ORDER BY MATCH(p.name, p.category_large_name, p.category_small_name, p.road_address, p.lot_address)
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
