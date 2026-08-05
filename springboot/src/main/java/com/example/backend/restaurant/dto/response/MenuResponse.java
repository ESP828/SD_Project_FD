package com.example.backend.restaurant.dto.response;

import com.example.backend.restaurant.domain.entity.Menu;

public record MenuResponse(
        Long menuId,
        String name,
        Integer price,
        String description,
        String imageUrl,
        boolean representative,
        String status
) {
    public static MenuResponse from(Menu menu) {
        return new MenuResponse(
                menu.getMenuId(),
                menu.getName(),
                menu.getPrice(),
                menu.getDescription(),
                menu.getImageUrl(),
                menu.isRepresentative(),
                menu.getStatus().name()
        );
    }
}
