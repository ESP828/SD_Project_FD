package com.example.backend.auth.repository;

import com.example.backend.auth.domain.entity.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    void deleteByExpiresAtBefore(LocalDateTime cutoff);

    @Modifying
    @Query("update RefreshToken t set t.revokedAt = :now where t.accountId = :accountId and t.revokedAt is null")
    void revokeAllForAccount(Long accountId, LocalDateTime now);
}
