package com.example.backend.admin.dto.response;

import com.example.backend.auth.domain.type.AuthorityCode;
import com.example.backend.auth.repository.AccountRepository.AdminAccountRow;

import java.time.LocalDateTime;
import java.util.Arrays;

public record AdminAccountResponse(
        Long accountId,
        String loginId,
        String nickname,
        String email,
        String status,
        String role,
        String roleLabel,
        LocalDateTime createdAt,
        LocalDateTime lastLoginAt
) {
    public static AdminAccountResponse from(AdminAccountRow row) {
        AuthorityCode role = Arrays.stream(AuthorityCode.values())
                .filter(code -> code.authorityId() == row.getHighestAuthorityId())
                .findFirst()
                .orElse(AuthorityCode.ROLE_USER);
        return new AdminAccountResponse(
                row.getAccountId(),
                row.getLoginId(),
                row.getNickname(),
                row.getEmail(),
                row.getStatus(),
                role.name(),
                role.displayName(),
                row.getCreatedAt(),
                row.getLastLoginAt()
        );
    }
}
