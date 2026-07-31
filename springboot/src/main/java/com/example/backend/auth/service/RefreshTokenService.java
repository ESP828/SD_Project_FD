package com.example.backend.auth.service;

import com.example.backend.auth.domain.entity.Account;
import com.example.backend.auth.domain.entity.RefreshToken;
import com.example.backend.auth.repository.AccountRepository;
import com.example.backend.auth.repository.RefreshTokenRepository;
import com.example.backend.global.exception.BusinessException;
import com.example.backend.global.exception.ErrorCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;

/**
 * 리프레시 토큰(로그인 상태 유지)을 발급·회전·폐기한다.
 * 원문 토큰은 httpOnly 쿠키로만 전달되고, DB에는 SHA-256 해시만 저장한다.
 */
@Service
public class RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;
    private final AccountRepository accountRepository;
    private final SecureRandom secureRandom = new SecureRandom();
    private final Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
    private final Duration validity;

    public RefreshTokenService(
            RefreshTokenRepository refreshTokenRepository,
            AccountRepository accountRepository,
            @Value("${jwt.refresh-token-validity:1209600000}") long validityMillis
    ) {
        if (validityMillis <= 0) {
            throw new IllegalStateException("리프레시 토큰 유효기간은 0보다 커야 합니다.");
        }
        this.refreshTokenRepository = refreshTokenRepository;
        this.accountRepository = accountRepository;
        this.validity = Duration.ofMillis(validityMillis);
    }

    @Transactional
    public String issue(Account account) {
        String rawToken = generateRawToken();
        LocalDateTime expiresAt = LocalDateTime.now().plus(validity);
        refreshTokenRepository.save(new RefreshToken(account.getAccountId(), hash(rawToken), expiresAt));
        return rawToken;
    }

    /**
     * 기존 토큰을 폐기하고 같은 계정으로 새 토큰을 발급한다(회전).
     */
    @Transactional
    public Rotated rotate(String rawToken) {
        RefreshToken existing = refreshTokenRepository.findByTokenHash(hash(rawToken))
                .filter(RefreshToken::isValid)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN));
        existing.revoke();
        Account account = accountRepository.findById(existing.getAccountId())
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN));
        return new Rotated(account, issue(account));
    }

    @Transactional
    public void revoke(String rawToken) {
        refreshTokenRepository.findByTokenHash(hash(rawToken)).ifPresent(RefreshToken::revoke);
    }

    /**
     * 비밀번호 재설정 등 계정 보안 이벤트 발생 시, 이미 발급된 모든 리프레시 토큰(다른 기기 포함)을 무효화한다.
     */
    @Transactional
    public void revokeAllForAccount(Long accountId) {
        refreshTokenRepository.revokeAllForAccount(accountId, LocalDateTime.now());
    }

    public Duration validity() {
        return validity;
    }

    /**
     * 만료된 리프레시 토큰 행을 매일 새벽 3시에 정리한다(테이블 무한 증가 방지).
     */
    @Scheduled(cron = "0 0 3 * * *")
    @Transactional
    public void purgeExpired() {
        refreshTokenRepository.deleteByExpiresAtBefore(LocalDateTime.now());
    }

    private String generateRawToken() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return encoder.encodeToString(bytes);
    }

    private String hash(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(rawToken.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 알고리즘을 사용할 수 없습니다.", exception);
        }
    }

    public record Rotated(Account account, String refreshToken) {
    }
}
