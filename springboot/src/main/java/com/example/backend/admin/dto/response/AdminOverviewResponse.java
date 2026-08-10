package com.example.backend.admin.dto.response;

import java.time.LocalDateTime;
import java.util.List;

public record AdminOverviewResponse(
        long accountCount,
        long pendingBusinessApplicationCount,
        long activeRestaurantCount,
        long communityPostCount,
        long activePresetCount,
        List<PendingBusinessApplication> pendingBusinessApplications
) {
    public AdminOverviewResponse {
        pendingBusinessApplications = List.copyOf(pendingBusinessApplications);
    }

    public record PendingBusinessApplication(
            Long applicationId,
            String applicantLoginId,
            String applicantNickname,
            String businessName,
            String representativeName,
            LocalDateTime createdAt
    ) {
    }
}
