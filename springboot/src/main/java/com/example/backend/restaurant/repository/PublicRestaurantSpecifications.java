package com.example.backend.restaurant.repository;

import com.example.backend.restaurant.domain.entity.PublicRestaurant;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.List;

public final class PublicRestaurantSpecifications {

    private PublicRestaurantSpecifications() {
    }

    /**
     * 검색어를 공백 기준으로 나눠서 각 단어가 상호명에 전부(순서 무관) 포함돼 있으면 매칭한다.
     * 예) "버거킹 신논현" -> name LIKE %버거킹% AND name LIKE %신논현%
     * 검색어 전체를 하나의 문자열로 취급해서 LIKE했을 때는, 실제 상호명(예: "버거킹신논현역점")에
     * 없는 공백이 검색어에 섞여 있으면 전혀 매칭이 안 되는 문제가 있었다.
     */
    public static Specification<PublicRestaurant> nameContains(String keyword) {
        return (root, query, cb) -> {
            if (!StringUtils.hasText(keyword)) {
                return cb.conjunction();
            }
            String[] tokens = keyword.trim().split("\\s+");
            Predicate[] predicates = Arrays.stream(tokens)
                    .filter(StringUtils::hasText)
                    .map(token -> cb.like(root.get("name"), "%" + token + "%"))
                    .toArray(Predicate[]::new);
            return predicates.length == 0 ? cb.conjunction() : cb.and(predicates);
        };
    }

    public static Specification<PublicRestaurant> regionContains(String region) {
        return (root, query, cb) -> {
            if (!StringUtils.hasText(region)) {
                return cb.conjunction();
            }
            String pattern = "%" + region.trim() + "%";
            return cb.or(
                    cb.like(root.get("roadAddress"), pattern),
                    cb.like(root.get("lotAddress"), pattern)
            );
        };
    }

    public static Specification<PublicRestaurant> categoryIn(List<String> categoryNames) {
        return (root, query, cb) -> {
            if (categoryNames == null || categoryNames.isEmpty()) {
                return cb.conjunction();
            }
            return root.get("categorySmallName").in(categoryNames);
        };
    }
}
