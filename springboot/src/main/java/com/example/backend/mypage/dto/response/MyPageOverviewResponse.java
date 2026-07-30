package com.example.backend.mypage.dto.response;

import com.example.backend.auth.domain.type.AccountStatus;
import com.example.backend.auth.domain.type.Gender;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record MyPageOverviewResponse(
        Long accountId,
        String loginId,
        String email,
        String nickname,
        Gender gender,
        LocalDate birthDate,
        String profileImageUrl,
        AccountStatus status,
        LocalDateTime createdAt,
        List<String> authorities,
        long favoriteCount,
        long reviewCount,
        long postCount,
        long commentCount,
        long unreadNotificationCount
) {
    public MyPageOverviewResponse {
        authorities = List.copyOf(authorities);
    }
}
