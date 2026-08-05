package com.example.backend.business.dto.response;

import com.example.backend.auth.domain.entity.Account;
import com.example.backend.business.domain.entity.BusinessApplication;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record BusinessApplicationResponse(
        Long applicationId,
        String applicantLoginId,
        String applicantNickname,
        String businessName,
        String businessNumber,
        String representativeName,
        LocalDate openedAt,
        String contact,
        String reason,
        String status,
        String rejectReason,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static BusinessApplicationResponse from(BusinessApplication application) {
        Account account = application.getAccount();
        return new BusinessApplicationResponse(
                application.getApplicationId(),
                account != null ? account.getLoginId() : null,
                account != null ? account.getNickname() : null,
                application.getBusinessName(),
                application.getBusinessNumber(),
                application.getRepresentativeName(),
                application.getOpenedAt(),
                application.getContact(),
                application.getReason(),
                application.getStatus().name(),
                application.getRejectReason(),
                application.getCreatedAt(),
                application.getUpdatedAt()
        );
    }
}
