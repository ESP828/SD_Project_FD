package com.example.backend.auth.service;

import com.example.backend.auth.domain.entity.Account;
import com.example.backend.auth.domain.entity.SocialAccount;
import com.example.backend.auth.domain.type.AuthorityCode;
import com.example.backend.auth.domain.type.SocialProvider;
import com.example.backend.auth.dto.response.AuthTokenResponse;
import com.example.backend.auth.integration.oauth.OAuthProviderClient;
import com.example.backend.auth.integration.oauth.OAuthUserProfile;
import com.example.backend.auth.repository.AccountRepository;
import com.example.backend.auth.repository.SocialAccountRepository;
import com.example.backend.global.exception.BusinessException;
import com.example.backend.global.exception.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.security.SecureRandom;
import java.util.EnumMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class SocialAuthService {

    private final Map<SocialProvider, OAuthProviderClient> clients;
    private final AccountRepository accountRepository;
    private final SocialAccountRepository socialAccountRepository;
    private final AuthorityService authorityService;
    private final TokenService tokenService;
    private final SecureRandom secureRandom = new SecureRandom();

    public SocialAuthService(
            List<OAuthProviderClient> providerClients,
            AccountRepository accountRepository,
            SocialAccountRepository socialAccountRepository,
            AuthorityService authorityService,
            TokenService tokenService
    ) {
        this.clients = new EnumMap<>(SocialProvider.class);
        providerClients.forEach(client -> clients.put(client.provider(), client));
        this.accountRepository = accountRepository;
        this.socialAccountRepository = socialAccountRepository;
        this.authorityService = authorityService;
        this.tokenService = tokenService;
    }

    public URI buildAuthorizationUri(SocialProvider provider, String state) {
        return client(provider).buildAuthorizationUri(state);
    }

    @Transactional
    public AuthTokenResponse login(
            SocialProvider provider,
            String authorizationCode,
            String state
    ) {
        OAuthUserProfile profile = client(provider).fetchUserProfile(authorizationCode, state);
        return socialAccountRepository
                .findByProviderAndProviderUserId(provider, profile.providerUserId())
                .map(socialAccount -> loginExisting(socialAccount, profile))
                .orElseGet(() -> registerAndLogin(profile));
    }

    private AuthTokenResponse loginExisting(
            SocialAccount socialAccount,
            OAuthUserProfile profile
    ) {
        Account account = accountRepository.findById(socialAccount.getAccountId())
                .orElseThrow(() -> new BusinessException(ErrorCode.ACCOUNT_UNAVAILABLE));
        if (!account.isActive()) {
            throw new BusinessException(ErrorCode.ACCOUNT_UNAVAILABLE);
        }
        socialAccount.updateSnapshot(
                normalizeEmail(profile.email()),
                normalizeNullable(profile.nickname())
        );
        account.markLoginSucceeded();
        return tokenService.issueAccessToken(account);
    }

    private AuthTokenResponse registerAndLogin(OAuthUserProfile profile) {
        String email = normalizeEmail(profile.email());
        if (email != null && accountRepository.existsByEmail(email)) {
            throw new BusinessException(ErrorCode.SOCIAL_LINK_REQUIRED);
        }
        String nickname = createUniqueNickname(profile);
        Account account = accountRepository.save(Account.social(email, nickname));
        socialAccountRepository.save(new SocialAccount(
                account.getAccountId(),
                profile.provider(),
                profile.providerUserId(),
                email,
                normalizeNullable(profile.nickname())
        ));
        authorityService.grant(account.getAccountId(), AuthorityCode.ROLE_USER);
        account.markLoginSucceeded();
        return tokenService.issueAccessToken(account);
    }

    private String createUniqueNickname(OAuthUserProfile profile) {
        String base = normalizeNullable(profile.nickname());
        if (base == null) {
            base = switch (profile.provider()) {
                case KAKAO -> "카카오회원";
                case NAVER -> "네이버회원";
                case GOOGLE -> "구글회원";
            };
        }
        base = base.replaceAll("\\s+", " ").trim();
        if (base.length() > 22) {
            base = base.substring(0, 22);
        }
        if (!accountRepository.existsByNickname(base)) {
            return base;
        }
        for (int attempt = 0; attempt < 10; attempt++) {
            byte[] random = new byte[4];
            secureRandom.nextBytes(random);
            String candidate = base + "_" + HexFormat.of().formatHex(random);
            if (candidate.length() > 30) {
                candidate = candidate.substring(0, 30);
            }
            if (!accountRepository.existsByNickname(candidate)) {
                return candidate;
            }
        }
        throw new BusinessException(ErrorCode.DATA_CONFLICT, "사용 가능한 닉네임을 생성하지 못했습니다.");
    }

    private String normalizeNullable(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String normalizeEmail(String value) {
        String normalized = normalizeNullable(value);
        return normalized == null ? null : normalized.toLowerCase(Locale.ROOT);
    }

    private OAuthProviderClient client(SocialProvider provider) {
        OAuthProviderClient client = clients.get(provider);
        if (client == null) {
            throw new BusinessException(ErrorCode.OAUTH_PROVIDER_FAILURE);
        }
        return client;
    }
}
