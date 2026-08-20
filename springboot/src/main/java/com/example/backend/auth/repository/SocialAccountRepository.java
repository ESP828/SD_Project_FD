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

    // 계정을 탈퇴시킬 때 소셜 계정 연동을 끊어서, 나중에 같은 소셜 계정으로 다시 가입할 수 있게 한다.
    // (연동이 안 끊기면 provider+providerUserId가 탈퇴한 옛 계정에 계속 묶여있어서 재가입이 막힌다.)
    void deleteByAccountId(Long accountId);
}
