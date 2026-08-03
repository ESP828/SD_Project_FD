package com.example.backend.auth.controller;

import com.example.backend.auth.domain.type.SocialProvider;
import com.example.backend.auth.dto.request.EmailVerificationConfirmRequest;
import com.example.backend.auth.dto.request.EmailVerificationRequest;
import com.example.backend.auth.dto.request.FindPasswordCodeRequest;
import com.example.backend.auth.dto.request.FindPasswordResetRequest;
import com.example.backend.auth.dto.request.LoginRequest;
import com.example.backend.auth.dto.request.OAuthTicketExchangeRequest;
import com.example.backend.auth.dto.request.SignupRequest;
import com.example.backend.auth.dto.response.AuthTokenResponse;
import com.example.backend.auth.dto.response.FindLoginIdResponse;
import com.example.backend.auth.dto.response.LoginIdAvailabilityResponse;
import com.example.backend.auth.integration.oauth.OAuthStateService;
import com.example.backend.auth.service.AuthService;
import com.example.backend.auth.service.EmailVerificationService;
import com.example.backend.auth.service.OAuthLoginTicketService;
import com.example.backend.auth.service.RefreshTokenService;
import com.example.backend.auth.service.SocialAuthService;
import com.example.backend.auth.service.TokenService;
import com.example.backend.global.exception.BusinessException;
import com.example.backend.global.exception.ErrorCode;
import com.example.backend.global.response.ApiResponse;
import com.example.backend.global.util.RateLimiter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.time.Duration;

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
    private static final String REFRESH_TOKEN_COOKIE = "FOODUCK_REFRESH_TOKEN";

    private final AuthService authService;
    private final SocialAuthService socialAuthService;
    private final EmailVerificationService emailVerificationService;
    private final OAuthStateService oauthStateService;
    private final OAuthLoginTicketService oauthLoginTicketService;
    private final RefreshTokenService refreshTokenService;
    private final TokenService tokenService;
    private final RateLimiter rateLimiter;
    private final String oauthSuccessUri;
    private final boolean secureCookies;

    public AuthController(
            AuthService authService,
            SocialAuthService socialAuthService,
            EmailVerificationService emailVerificationService,
            OAuthStateService oauthStateService,
            OAuthLoginTicketService oauthLoginTicketService,
            RefreshTokenService refreshTokenService,
            TokenService tokenService,
            RateLimiter rateLimiter,
            @Value("${app.oauth-success-uri}") String oauthSuccessUri,
            @Value("${app.security.secure-cookies:false}") boolean secureCookies
    ) {
        this.authService = authService;
        this.socialAuthService = socialAuthService;
        this.emailVerificationService = emailVerificationService;
        this.oauthStateService = oauthStateService;
        this.oauthLoginTicketService = oauthLoginTicketService;
        this.refreshTokenService = refreshTokenService;
        this.tokenService = tokenService;
        this.rateLimiter = rateLimiter;
        this.oauthSuccessUri = oauthSuccessUri;
        this.secureCookies = secureCookies;
    }

    @PostMapping("/signup")
    public ApiResponse<Void> signup(@Valid @RequestBody SignupRequest request) {
        authService.signup(request);
        return ApiResponse.success("회원가입이 완료되었습니다.", null);
    }

    @GetMapping("/check-login-id")
    public ApiResponse<LoginIdAvailabilityResponse> checkLoginId(
            @RequestParam String loginId,
            HttpServletRequest servletRequest
    ) {
        if (!StringUtils.hasText(loginId)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        requireWithinLimit("check-login-id:" + clientIp(servletRequest), 20, Duration.ofMinutes(1));
        boolean available = authService.isLoginIdAvailable(loginId);
        return ApiResponse.success(
                available ? "사용할 수 있는 아이디입니다." : "이미 사용 중인 아이디입니다.",
                new LoginIdAvailabilityResponse(available)
        );
    }

    @PostMapping("/email/verification-code")
    public ApiResponse<Void> sendEmailVerificationCode(
            @Valid @RequestBody EmailVerificationRequest request,
            HttpServletRequest servletRequest
    ) {
        requireWithinLimit("email-code-ip:" + clientIp(servletRequest), 5, Duration.ofMinutes(10));
        requireWithinLimit("email-code-addr:" + request.email().trim().toLowerCase(), 3, Duration.ofMinutes(10));
        emailVerificationService.sendCode(request.email());
        return ApiResponse.success("인증번호를 발송했습니다.", null);
    }

    @PostMapping("/email/verify")
    public ApiResponse<Void> verifyEmailCode(
            @Valid @RequestBody EmailVerificationConfirmRequest request,
            HttpServletRequest servletRequest
    ) {
        requireWithinLimit("email-verify-ip:" + clientIp(servletRequest), 15, Duration.ofMinutes(10));
        emailVerificationService.confirmCode(request.email(), request.code());
        return ApiResponse.success("이메일 인증이 완료되었습니다.", null);
    }

    @PostMapping("/find-id/verification-code")
    public ApiResponse<Void> sendFindIdVerificationCode(
            @Valid @RequestBody EmailVerificationRequest request,
            HttpServletRequest servletRequest
    ) {
        requireWithinLimit("find-id-code-ip:" + clientIp(servletRequest), 5, Duration.ofMinutes(10));
        requireWithinLimit("find-id-code-addr:" + request.email().trim().toLowerCase(), 3, Duration.ofMinutes(10));
        emailVerificationService.sendRecoveryCode(request.email());
        return ApiResponse.success("인증번호를 발송했습니다.", null);
    }

    @PostMapping("/find-id/verify")
    public ApiResponse<FindLoginIdResponse> verifyFindId(
            @Valid @RequestBody EmailVerificationConfirmRequest request,
            HttpServletRequest servletRequest
    ) {
        requireWithinLimit("find-id-verify-ip:" + clientIp(servletRequest), 15, Duration.ofMinutes(10));
        FindLoginIdResponse response = authService.findLoginId(request.email(), request.code());
        return ApiResponse.success("아이디를 확인했습니다.", response);
    }

    @PostMapping("/find-password/verification-code")
    public ApiResponse<Void> sendFindPasswordVerificationCode(
            @Valid @RequestBody FindPasswordCodeRequest request,
            HttpServletRequest servletRequest
    ) {
        requireWithinLimit("find-pw-code-ip:" + clientIp(servletRequest), 5, Duration.ofMinutes(10));
        requireWithinLimit("find-pw-code-addr:" + request.email().trim().toLowerCase(), 3, Duration.ofMinutes(10));
        authService.requestPasswordResetCode(request.loginId(), request.email());
        return ApiResponse.success("인증번호를 발송했습니다.", null);
    }

    @PostMapping("/find-password/verify")
    public ApiResponse<Void> verifyFindPassword(
            @Valid @RequestBody FindPasswordResetRequest request,
            HttpServletRequest servletRequest
    ) {
        requireWithinLimit("find-pw-verify-ip:" + clientIp(servletRequest), 15, Duration.ofMinutes(10));
        authService.resetPassword(request.loginId(), request.email(), request.code());
        return ApiResponse.success("임시 비밀번호를 이메일로 발송했습니다.", null);
    }

    private void requireWithinLimit(String key, int maxRequests, Duration window) {
        if (!rateLimiter.allow(key, maxRequests, window)) {
            throw new BusinessException(ErrorCode.RATE_LIMIT_EXCEEDED);
        }
    }

    private String clientIp(HttpServletRequest request) {
        return request.getRemoteAddr();
    }

    @PostMapping("/login")
    public ApiResponse<AuthTokenResponse> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletResponse response
    ) {
        AuthService.LoginResult result = authService.login(request);
        if (result.refreshToken() != null) {
            setRefreshTokenCookie(response, result.refreshToken(), refreshTokenService.validity());
        }
        return ApiResponse.success("로그인에 성공했습니다.", result.accessToken());
    }

    @PostMapping("/refresh")
    public ApiResponse<AuthTokenResponse> refresh(
            @CookieValue(name = REFRESH_TOKEN_COOKIE, required = false) String refreshToken,
            HttpServletRequest servletRequest,
            HttpServletResponse servletResponse
    ) {
        requireWithinLimit("refresh-ip:" + clientIp(servletRequest), 30, Duration.ofMinutes(10));
        if (!StringUtils.hasText(refreshToken)) {
            throw new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN);
        }
        RefreshTokenService.Rotated rotated = refreshTokenService.rotate(refreshToken);
        setRefreshTokenCookie(servletResponse, rotated.refreshToken(), refreshTokenService.validity());
        return ApiResponse.success("토큰이 갱신되었습니다.", tokenService.issueAccessToken(rotated.account()));
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(
            @CookieValue(name = REFRESH_TOKEN_COOKIE, required = false) String refreshToken,
            HttpServletResponse response
    ) {
        if (StringUtils.hasText(refreshToken)) {
            refreshTokenService.revoke(refreshToken);
        }
        expireRefreshTokenCookie(response);
        return ApiResponse.success("로그아웃되었습니다.", null);
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

    private void setRefreshTokenCookie(HttpServletResponse response, String refreshToken, Duration validity) {
        ResponseCookie cookie = ResponseCookie.from(REFRESH_TOKEN_COOKIE, refreshToken)
                .httpOnly(true)
                .secure(secureCookies)
                .sameSite("Lax")
                .path("/api/auth")
                .maxAge(validity)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private void expireRefreshTokenCookie(HttpServletResponse response) {
        ResponseCookie expiredCookie = ResponseCookie.from(REFRESH_TOKEN_COOKIE, "")
                .httpOnly(true)
                .secure(secureCookies)
                .sameSite("Lax")
                .path("/api/auth")
                .maxAge(0)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, expiredCookie.toString());
    }
}
