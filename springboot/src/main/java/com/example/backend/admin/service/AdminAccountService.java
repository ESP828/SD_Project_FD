package com.example.backend.admin.service;

import com.example.backend.admin.dto.request.AdminAccountUpdateRequest;
import com.example.backend.admin.dto.response.AdminAccountResponse;
import com.example.backend.auth.domain.entity.Account;
import com.example.backend.auth.domain.type.AccountStatus;
import com.example.backend.auth.domain.type.AuthorityCode;
import com.example.backend.auth.repository.AccountRepository;
import com.example.backend.auth.service.AuthorityService;
import com.example.backend.auth.service.RefreshTokenService;
import com.example.backend.global.exception.BusinessException;
import com.example.backend.global.exception.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class AdminAccountService {

    private static final int MAX_RESULTS = 100;

    private final AccountRepository accountRepository;
    private final AuthorityService authorityService;
    private final RefreshTokenService refreshTokenService;

    public AdminAccountService(
            AccountRepository accountRepository,
            AuthorityService authorityService,
            RefreshTokenService refreshTokenService
    ) {
        this.accountRepository = accountRepository;
        this.authorityService = authorityService;
        this.refreshTokenService = refreshTokenService;
    }

    @Transactional(readOnly = true)
    public List<AdminAccountResponse> search(String keyword, String roleFilter) {
        String normalizedKeyword = keyword == null ? "" : keyword.trim();
        int roleId = AuthorityCode.fromCode(roleFilter)
                .map(code -> (int) code.authorityId())
                .orElse(-1);
        return accountRepository.searchAccounts(normalizedKeyword, roleId, MAX_RESULTS).stream()
                .map(AdminAccountResponse::from)
                .toList();
    }

    @Transactional
    public void update(Long actingAccountId, Long targetAccountId, AdminAccountUpdateRequest request) {
        requireNotSelf(actingAccountId, targetAccountId);
        Account account = accountRepository.findById(targetAccountId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ACCOUNT_NOT_FOUND));

        AuthorityCode role = AuthorityCode.fromCode(request.role())
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_INPUT));
        authorityService.setRole(targetAccountId, role);

        AccountStatus status = parseStatus(request.status());
        applyStatus(account, status);

        if (status == AccountStatus.SUSPENDED || status == AccountStatus.WITHDRAWN) {
            refreshTokenService.revokeAllForAccount(targetAccountId);
        }
    }

    @Transactional
    public void delete(Long actingAccountId, Long targetAccountId) {
        requireNotSelf(actingAccountId, targetAccountId);
        Account account = accountRepository.findById(targetAccountId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ACCOUNT_NOT_FOUND));
        account.withdraw();
        refreshTokenService.revokeAllForAccount(targetAccountId);
    }

    private void requireNotSelf(Long actingAccountId, Long targetAccountId) {
        if (actingAccountId.equals(targetAccountId)) {
            throw new BusinessException(ErrorCode.ADMIN_SELF_ACTION_NOT_ALLOWED);
        }
    }

    private AccountStatus parseStatus(String raw) {
        try {
            return AccountStatus.valueOf(raw);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
    }

    private void applyStatus(Account account, AccountStatus status) {
        switch (status) {
            case ACTIVE -> account.activate();
            case SUSPENDED -> account.suspend();
            case WITHDRAWN -> account.withdraw();
            case INACTIVE -> throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
    }
}
