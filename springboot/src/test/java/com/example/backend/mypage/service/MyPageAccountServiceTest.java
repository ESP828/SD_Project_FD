package com.example.backend.mypage.service;

import com.example.backend.auth.domain.entity.Account;
import com.example.backend.auth.domain.entity.AccountCredential;
import com.example.backend.auth.domain.type.AccountStatus;
import com.example.backend.auth.repository.AccountCredentialRepository;
import com.example.backend.auth.repository.AccountRepository;
import com.example.backend.auth.service.RefreshTokenService;
import com.example.backend.global.exception.BusinessException;
import com.example.backend.global.exception.ErrorCode;
import com.example.backend.mypage.dto.request.WithdrawAccountRequest;
import com.example.backend.restaurant.domain.entity.Restaurant;
import com.example.backend.restaurant.domain.type.RestaurantStatus;
import com.example.backend.restaurant.repository.RestaurantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MyPageAccountServiceTest {

    @Mock AccountRepository accountRepository;
    @Mock AccountCredentialRepository credentialRepository;
    @Mock RestaurantRepository restaurantRepository;
    @Mock PasswordEncoder passwordEncoder;
    @Mock RefreshTokenService refreshTokenService;
    @Mock AccountCredential credential;
    @Mock Restaurant restaurant;

    private MyPageAccountService service;

    @BeforeEach
    void setUp() {
        service = new MyPageAccountService(
                accountRepository,
                credentialRepository,
                restaurantRepository,
                passwordEncoder,
                refreshTokenService
        );
    }

    @Test
    void localAccountRequiresPasswordAndDeactivatesOwnedRestaurants() {
        Account account = account();
        when(accountRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(account));
        when(credentialRepository.findById(1L)).thenReturn(Optional.of(credential));
        when(credential.getPasswordHash()).thenReturn("encoded-password");
        when(passwordEncoder.matches("Correct1!", "encoded-password")).thenReturn(true);
        when(restaurantRepository.findAllByOwnerAccountIdAndStatus(1L, RestaurantStatus.ACTIVE))
                .thenReturn(List.of(restaurant));

        service.withdraw(1L, new WithdrawAccountRequest("Correct1!", "회원탈퇴"));

        assertEquals(AccountStatus.WITHDRAWN, account.getStatus());
        assertNotNull(account.getDeletedAt());
        verify(restaurant).deactivate();
        verify(refreshTokenService).revokeAllForAccount(1L);
    }

    @Test
    void wrongPasswordLeavesAccountAndRestaurantsUnchanged() {
        Account account = account();
        when(accountRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(account));
        when(credentialRepository.findById(1L)).thenReturn(Optional.of(credential));
        when(credential.getPasswordHash()).thenReturn("encoded-password");
        when(passwordEncoder.matches("wrong", "encoded-password")).thenReturn(false);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service.withdraw(1L, new WithdrawAccountRequest("wrong", "회원탈퇴"))
        );

        assertEquals(ErrorCode.CURRENT_PASSWORD_MISMATCH, exception.getErrorCode());
        assertEquals(AccountStatus.ACTIVE, account.getStatus());
        verify(restaurantRepository, never())
                .findAllByOwnerAccountIdAndStatus(1L, RestaurantStatus.ACTIVE);
        verify(refreshTokenService, never()).revokeAllForAccount(1L);
    }

    @Test
    void socialAccountCanWithdrawWithExplicitConfirmation() {
        Account account = socialAccount();
        when(accountRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(account));
        when(credentialRepository.findById(1L)).thenReturn(Optional.empty());
        when(restaurantRepository.findAllByOwnerAccountIdAndStatus(1L, RestaurantStatus.ACTIVE))
                .thenReturn(List.of());

        service.withdraw(1L, new WithdrawAccountRequest(null, " 회원탈퇴 "));

        assertEquals(AccountStatus.WITHDRAWN, account.getStatus());
        verify(refreshTokenService).revokeAllForAccount(1L);
    }

    @Test
    void confirmationMustMatchExactlyAfterTrimming() {
        Account account = account();
        when(accountRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(account));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service.withdraw(1L, new WithdrawAccountRequest("Correct1!", "탈퇴"))
        );

        assertEquals(ErrorCode.INVALID_INPUT, exception.getErrorCode());
        assertEquals(AccountStatus.ACTIVE, account.getStatus());
        verify(refreshTokenService, never()).revokeAllForAccount(1L);
    }

    private Account account() {
        Account account = Account.local("withdraw-user", "withdraw@example.com", "탈퇴회원");
        ReflectionTestUtils.setField(account, "accountId", 1L);
        return account;
    }

    private Account socialAccount() {
        Account account = Account.social("withdraw-social@example.com", "소셜탈퇴회원");
        ReflectionTestUtils.setField(account, "accountId", 1L);
        return account;
    }
}
