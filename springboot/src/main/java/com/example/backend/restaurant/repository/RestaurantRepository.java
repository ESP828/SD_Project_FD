package com.example.backend.restaurant.repository;

import com.example.backend.restaurant.domain.entity.Restaurant;
import com.example.backend.restaurant.domain.type.RestaurantStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface RestaurantRepository extends JpaRepository<Restaurant, Long> {

    List<Restaurant> findByStatus(RestaurantStatus status);

    boolean existsByName(String name);

    /**
     * 관리자용 검색: 상호명·주소로 검색하고 상태로 필터링한다.
     * keyword가 빈 문자열이면 검색 조건을 생략하고, status가 null이면 상태 필터를 생략한다.
     */
    @Query("SELECT r FROM Restaurant r WHERE "
            + "(:status IS NULL OR r.status = :status) AND "
            + "(:keyword = '' OR r.name LIKE CONCAT('%', :keyword, '%') OR r.address LIKE CONCAT('%', :keyword, '%')) "
            + "ORDER BY r.createdAt DESC")
    List<Restaurant> search(
            @Param("keyword") String keyword,
            @Param("status") RestaurantStatus status,
            Pageable pageable
    );

}
