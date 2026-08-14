package com.example.backend.mypage.service;

import com.example.backend.auth.domain.entity.Account;
import com.example.backend.auth.domain.entity.AccountCredential;
import com.example.backend.auth.repository.AccountCredentialRepository;
import com.example.backend.auth.repository.AccountRepository;
import com.example.backend.auth.service.RefreshTokenService;
import com.example.backend.global.exception.BusinessException;
import com.example.backend.global.exception.ErrorCode;
import com.example.backend.mypage.dto.request.WithdrawAccountRequest;
import com.example.backend.restaurant.domain.type.RestaurantStatus;
import com.example.backend.restaurant.repository.RestaurantRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MyPageAccountService {

    private static final String WITHDRAW_CONFIRMATION = "회원탈퇴";

    private final AccountRepository accountRepository;
    private final AccountCredentialRepository credentialRepository;
    private final RestaurantRepository restaurantRepository;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenService refreshTokenService;

    public MyPageAccountService(
            AccountRepository accountRepository,
            AccountCredentialRepository credentialRepository,
            RestaurantRepository restaurantRepository,
            PasswordEncoder passwordEncoder,
            RefreshTokenService refreshTokenService
    ) {
        this.accountRepository = accountRepository;
        this.credentialRepository = credentialRepository;
        this.restaurantRepository = restaurantRepository;
        this.passwordEncoder = passwordEncoder;
        this.refreshTokenService = refreshTokenService;
    }

    @Transactional
    public void withdraw(Long accountId, WithdrawAccountRequest request) {
        Account account = accountRepository.findByIdForUpdate(accountId)
                .filter(Account::isActive)
                .orElseThrow(() -> new BusinessException(ErrorCode.ACCOUNT_UNAVAILABLE));

        validateConfirmation(request.confirmation());
        credentialRepository.findById(accountId).ifPresentOrElse(
                credential -> validatePassword(credential, request.currentPassword()),
                () -> validateSocialAccount(account)
        );

        restaurantRepository.findAllByOwnerAccountIdAndStatus(accountId, RestaurantStatus.ACTIVE)
                .forEach(restaurant -> restaurant.deactivate());
        account.withdraw();
        refreshTokenService.revokeAllForAccount(accountId);
    }

    private void validateConfirmation(String confirmation) {
        String normalized = confirmation == null ? "" : confirmation.trim();
        if (!WITHDRAW_CONFIRMATION.equals(normalized)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "회원 탈퇴 확인 문구가 일치하지 않습니다.");
        }
    }

    private void validatePassword(AccountCredential credential, String currentPassword) {
        if (currentPassword == null
                || !passwordEncoder.matches(currentPassword, credential.getPasswordHash())) {
            throw new BusinessException(ErrorCode.CURRENT_PASSWORD_MISMATCH);
        }
    }

    private void validateSocialAccount(Account account) {
        if (account.getLoginId() != null) {
            throw new BusinessException(ErrorCode.ACCOUNT_UNAVAILABLE);
        }
    }
}
