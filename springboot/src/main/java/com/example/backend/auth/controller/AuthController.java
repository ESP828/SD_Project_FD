package com.example.backend.auth.controller;

import com.example.backend.auth.domain.type.SocialProvider;
import com.example.backend.auth.dto.request.LoginRequest;
import com.example.backend.auth.dto.request.OAuthTicketExchangeRequest;
import com.example.backend.auth.dto.request.SignupRequest;
import com.example.backend.auth.dto.response.AuthTokenResponse;
import com.example.backend.auth.integration.oauth.OAuthStateService;
import com.example.backend.auth.service.AuthService;
import com.example.backend.auth.service.OAuthLoginTicketService;
import com.example.backend.auth.service.SocialAuthService;
import com.example.backend.global.exception.BusinessException;
import com.example.backend.global.exception.ErrorCode;
import com.example.backend.global.response.ApiResponse;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;

/**
 * 로컬 계정과 소셜 로그인 API.
 *
 * <p>소셜 콜백은 JWT를 URL에 직접 넣지 않는다. 브라우저에는 짧게 유효한
 * 일회용 교환 티켓만 전달하고, 정적 콜백 화면이 POST 요청으로 JWT를 교환한다.</p>
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final String OAUTH_STATE_COOKIE = "FOODUCK_OAUTH_STATE";

    private final AuthService authService;
    private final SocialAuthService socialAuthService;
    private final OAuthStateService oauthStateService;
    private final OAuthLoginTicketService oauthLoginTicketService;
    private final String oauthSuccessUri;
    private final boolean secureCookies;

    public AuthController(
            AuthService authService,
            SocialAuthService socialAuthService,
            OAuthStateService oauthStateService,
            OAuthLoginTicketService oauthLoginTicketService,
            @Value("${app.oauth-success-uri}") String oauthSuccessUri,
            @Value("${app.security.secure-cookies:false}") boolean secureCookies
    ) {
        this.authService = authService;
        this.socialAuthService = socialAuthService;
        this.oauthStateService = oauthStateService;
        this.oauthLoginTicketService = oauthLoginTicketService;
        this.oauthSuccessUri = oauthSuccessUri;
        this.secureCookies = secureCookies;
    }

    @PostMapping("/signup")
    public ApiResponse<Void> signup(@Valid @RequestBody SignupRequest request) {
        authService.signup(request);
        return ApiResponse.success("회원가입이 완료되었습니다.", null);
    }

    @PostMapping("/login")
    public ApiResponse<AuthTokenResponse> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.success("로그인에 성공했습니다.", authService.login(request));
    }

    /**
     * `/kakao/login` 등 SB가 사용하던 경로와 `/oauth/kakao/login`을 함께 지원한다.
     */
    @GetMapping({"/oauth/{provider}/login", "/{provider}/login"})
    public ResponseEntity<Void> beginSocialLogin(@PathVariable String provider) {
        SocialProvider socialProvider = SocialProvider.fromPath(provider);
        String state = oauthStateService.issue(socialProvider);
        URI authorizationUri = socialAuthService.buildAuthorizationUri(socialProvider, state);
        ResponseCookie stateCookie = ResponseCookie.from(OAUTH_STATE_COOKIE, state)
                .httpOnly(true)
                .secure(secureCookies)
                .sameSite("Lax")
                .path("/api/auth")
                .maxAge(oauthStateService.validity())
                .build();
        return ResponseEntity.status(302)
                .header(HttpHeaders.LOCATION, authorizationUri.toString())
                .header(HttpHeaders.SET_COOKIE, stateCookie.toString())
                .build();
    }

    @GetMapping({"/oauth/{provider}/callback", "/{provider}/callback"})
    public ResponseEntity<Void> socialLoginCallback(
            @PathVariable String provider,
            @RequestParam(required = false) String code,
            @RequestParam(required = false) String state,
            @RequestParam(required = false) String error,
            @org.springframework.web.bind.annotation.CookieValue(
                    name = OAUTH_STATE_COOKIE,
                    required = false
            ) String browserState,
            HttpServletResponse servletResponse
    ) {
        expireOAuthStateCookie(servletResponse);
        SocialProvider socialProvider = SocialProvider.fromPath(provider);
        if (StringUtils.hasText(error)) {
            throw new BusinessException(ErrorCode.OAUTH_AUTHORIZATION_DENIED);
        }
        if (!StringUtils.hasText(code) || !StringUtils.hasText(state)) {
            throw new BusinessException(ErrorCode.OAUTH_PROVIDER_FAILURE);
        }

        oauthStateService.validateForBrowser(socialProvider, state, browserState);
        AuthTokenResponse tokenResponse = socialAuthService.login(socialProvider, code, state);
        String ticket = oauthLoginTicketService.issue(tokenResponse);
        URI redirectUri = UriComponentsBuilder.fromUriString(oauthSuccessUri)
                .queryParam("ticket", ticket)
                .build()
                .encode()
                .toUri();
        return ResponseEntity.status(302)
                .header(HttpHeaders.LOCATION, redirectUri.toString())
                .build();
    }

    @PostMapping("/oauth/exchange")
    public ApiResponse<AuthTokenResponse> exchangeOAuthTicket(
            @Valid @RequestBody OAuthTicketExchangeRequest request
    ) {
        return ApiResponse.success(
                "소셜 로그인에 성공했습니다.",
                oauthLoginTicketService.consume(request.ticket())
        );
    }

    private void expireOAuthStateCookie(HttpServletResponse response) {
        ResponseCookie expiredCookie = ResponseCookie.from(OAUTH_STATE_COOKIE, "")
                .httpOnly(true)
                .secure(secureCookies)
                .sameSite("Lax")
                .path("/api/auth")
                .maxAge(0)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, expiredCookie.toString());
    }
}
