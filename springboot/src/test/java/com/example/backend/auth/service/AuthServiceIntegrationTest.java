package com.example.backend.auth.service;

import com.example.backend.auth.domain.entity.Account;
import com.example.backend.auth.domain.type.AuthorityCode;
import com.example.backend.auth.domain.type.SocialProvider;
import com.example.backend.auth.dto.request.LoginRequest;
import com.example.backend.auth.dto.request.SignupRequest;
import com.example.backend.auth.dto.response.AuthTokenResponse;
import com.example.backend.auth.integration.oauth.OAuthProviderClient;
import com.example.backend.auth.integration.oauth.OAuthUserProfile;
import com.example.backend.auth.repository.AccountAuthorityRepository;
import com.example.backend.auth.repository.AccountCredentialRepository;
import com.example.backend.auth.repository.AccountRepository;
import com.example.backend.auth.repository.SocialAccountRepository;
import com.example.backend.global.exception.BusinessException;
import com.example.backend.global.security.jwt.JwtProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AuthServiceIntegrationTest {

    @Autowired
    private AuthService authService;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private AccountCredentialRepository credentialRepository;

    @Autowired
    private AccountAuthorityRepository accountAuthorityRepository;

    @Autowired
    private SocialAccountRepository socialAccountRepository;

    @Autowired
    private AuthorityService authorityService;

    @Autowired
    private TokenService tokenService;

    @Autowired
    private JwtProvider jwtProvider;

    @Test
    void localSignupSeparatesCredentialAndIssuesRoleAwareJwt() {
        SignupRequest signup = new SignupRequest(
                "localtester",
                "correct-password",
                "localtester@example.com",
                "로컬테스터"
        );

        authService.signup(signup);

        Account account = accountRepository.findByLoginId("localtester").orElseThrow();
        String storedHash = credentialRepository.findById(account.getAccountId())
                .orElseThrow()
                .getPasswordHash();
        assertNotEquals(signup.password(), storedHash);
        assertTrue(storedHash.startsWith("$argon2"));
        assertEquals(List.of("ROLE_USER"), authorityService.findCodes(account.getAccountId()));

        AuthTokenResponse token = authService.login(
                new LoginRequest("localtester", "correct-password")
        );
        assertEquals("Bearer", token.tokenType());
        assertEquals(account.getAccountId(), jwtProvider.getAccountId(token.token()));
        assertEquals(List.of("ROLE_USER"), jwtProvider.getAuthorities(token.token()));
        assertNotNull(account.getLastLoginAt());

        assertThrows(
                BusinessException.class,
                () -> authService.login(new LoginRequest("localtester", "wrong-password"))
        );
    }

    @Test
    void socialSignupDoesNotCreateFakePasswordOrFakeEmail() {
        OAuthProviderClient kakao = fixedClient(new OAuthUserProfile(
                SocialProvider.KAKAO,
                "kakao-user-101",
                null,
                "카카오사용자"
        ));
        SocialAuthService socialAuthService = new SocialAuthService(
                List.of(kakao),
                accountRepository,
                socialAccountRepository,
                authorityService,
                tokenService
        );

        AuthTokenResponse token = socialAuthService.login(
                SocialProvider.KAKAO,
                "authorization-code",
                "validated-state"
        );

        var social = socialAccountRepository
                .findByProviderAndProviderUserId(SocialProvider.KAKAO, "kakao-user-101")
                .orElseThrow();
        Account account = accountRepository.findById(social.getAccountId()).orElseThrow();
        assertEquals(null, account.getLoginId());
        assertEquals(null, account.getEmail());
        assertFalse(credentialRepository.existsById(account.getAccountId()));
        assertEquals(account.getAccountId(), jwtProvider.getAccountId(token.token()));
        assertEquals(List.of("ROLE_USER"), authorityService.findCodes(account.getAccountId()));
    }

    @Test
    void socialSignupRequiresExplicitLinkWhenEmailAlreadyBelongsToLocalAccount() {
        authService.signup(new SignupRequest(
                "linktester",
                "correct-password",
                "same-email@example.com",
                "연결테스터"
        ));
        OAuthProviderClient google = fixedClient(new OAuthUserProfile(
                SocialProvider.GOOGLE,
                "google-user-202",
                "same-email@example.com",
                "Google User"
        ));
        SocialAuthService socialAuthService = new SocialAuthService(
                List.of(google),
                accountRepository,
                socialAccountRepository,
                authorityService,
                tokenService
        );

        assertThrows(
                BusinessException.class,
                () -> socialAuthService.login(
                        SocialProvider.GOOGLE,
                        "authorization-code",
                        "validated-state"
                )
        );
    }

    @Test
    void authorityHierarchyIsCumulativeAndMissingMappingDefaultsToUser() {
        Account account = accountRepository.save(Account.local(
                "authoritytester",
                "authoritytester@example.com",
                "권한테스터"
        ));

        assertEquals(List.of("ROLE_USER"), authorityService.findCodes(account.getAccountId()));

        authorityService.grant(account.getAccountId(), AuthorityCode.ROLE_BUSINESS);
        assertEquals(
                List.of("ROLE_USER", "ROLE_BUSINESS"),
                authorityService.findCodes(account.getAccountId())
        );

        authorityService.grant(account.getAccountId(), AuthorityCode.ROLE_ADMIN);
        assertEquals(
                List.of("ROLE_USER", "ROLE_BUSINESS", "ROLE_ADMIN"),
                authorityService.findCodes(account.getAccountId())
        );
        assertEquals(
                List.of((short) 0, (short) 1, (short) 2),
                accountAuthorityRepository.findAllByIdAccountId(account.getAccountId())
                        .stream()
                        .map(authority -> authority.getAuthorityId())
                        .sorted()
                        .toList()
        );
    }

    private OAuthProviderClient fixedClient(OAuthUserProfile profile) {
        return new OAuthProviderClient() {
            @Override
            public SocialProvider provider() {
                return profile.provider();
            }

            @Override
            public URI buildAuthorizationUri(String state) {
                return URI.create("https://example.test/oauth?state=" + state);
            }

            @Override
            public OAuthUserProfile fetchUserProfile(String authorizationCode, String state) {
                return profile;
            }
        };
    }
}
