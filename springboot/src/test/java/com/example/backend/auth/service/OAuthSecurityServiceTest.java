package com.example.backend.auth.service;

import com.example.backend.auth.domain.type.SocialProvider;
import com.example.backend.auth.dto.response.AuthTokenResponse;
import com.example.backend.auth.integration.oauth.OAuthStateService;
import com.example.backend.global.exception.BusinessException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OAuthSecurityServiceTest {

    private static final String STATE_SECRET =
            "unit-test-oauth-state-secret-is-at-least-thirty-two-bytes";

    @Test
    void signedStateRejectsTamperingAndWrongProvider() {
        OAuthStateService service = new OAuthStateService(STATE_SECRET, 60_000);
        String state = service.issue(SocialProvider.KAKAO);

        service.validate(SocialProvider.KAKAO, state);
        service.validateForBrowser(SocialProvider.KAKAO, state, state);
        assertThrows(
                BusinessException.class,
                () -> service.validate(SocialProvider.NAVER, state)
        );
        assertThrows(
                BusinessException.class,
                () -> service.validateForBrowser(
                        SocialProvider.KAKAO,
                        state,
                        "another-browser-state"
                )
        );
        assertThrows(
                BusinessException.class,
                () -> service.validate(SocialProvider.KAKAO, state + "tampered")
        );
    }

    @Test
    void oauthTicketCanOnlyBeConsumedOnce() {
        OAuthLoginTicketService service = new OAuthLoginTicketService(60_000);
        AuthTokenResponse expected = AuthTokenResponse.bearer("signed-jwt", 1_800_000);
        String ticket = service.issue(expected);

        assertEquals(expected, service.consume(ticket));
        assertThrows(BusinessException.class, () -> service.consume(ticket));
    }
}
