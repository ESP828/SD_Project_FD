package com.example.backend.global.response;

import java.util.List;

/**
 * 목록형 API가 공통으로 쓰는 페이지 응답. board/preset이 쓰던 모양(content/totalElements/
 * totalPages/first/last)을 그대로 따라서, 프론트의 공용 페이지네이션 컴포넌트
 * (js/common.js의 FooduckPagination)가 화면마다 다시 어댑터를 만들 필요 없이 재사용할 수 있다.
 */
public record PageResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean first,
        boolean last
) {
    public static <T> PageResponse<T> of(List<T> content, int page, int size, long totalElements) {
        int totalPages = size <= 0 ? 1 : Math.max(1, (int) Math.ceil((double) totalElements / size));
        return new PageResponse<>(
                List.copyOf(content),
                page,
                size,
                totalElements,
                totalPages,
                page <= 0,
                page >= totalPages - 1
        );
    }
}
