package com.example.backend.auth.repository;

import com.example.backend.auth.domain.entity.SocialAccount;
import com.example.backend.auth.domain.type.SocialProvider;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SocialAccountRepository extends JpaRepository<SocialAccount, Long> {

    Optional<SocialAccount> findByProviderAndProviderUserId(
            SocialProvider provider,
            String providerUserId
    );

    boolean existsByAccountIdAndProvider(Long accountId, SocialProvider provider);
}
