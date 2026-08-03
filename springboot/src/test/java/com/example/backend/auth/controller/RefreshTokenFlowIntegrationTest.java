package com.example.backend.auth.controller;

import com.example.backend.auth.domain.entity.EmailVerification;
import com.example.backend.auth.repository.EmailVerificationRepository;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * "로그인 상태 유지" 체크 시 발급되는 리프레시 토큰 쿠키의 발급·회전·로그아웃 폐기를 검증한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class RefreshTokenFlowIntegrationTest {

    private static final String REFRESH_COOKIE = "FOODUCK_REFRESH_TOKEN";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EmailVerificationRepository emailVerificationRepository;

    private void verifyEmail(String email) {
        EmailVerification verification = new EmailVerification(
                email, "000000", LocalDateTime.now().plusMinutes(5)
        );
        verification.markVerified(LocalDateTime.now().plusMinutes(30));
        emailVerificationRepository.save(verification);
    }

    private void signup(String loginId, String email, String nickname) throws Exception {
        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "loginId": "%s",
                                  "password": "correct-password1!",
                                  "passwordConfirm": "correct-password1!",
                                  "email": "%s",
                                  "nickname": "%s"
                                }
                                """.formatted(loginId, email, nickname)))
                .andExpect(status().isOk());
    }

    @Test
    void rememberLoginIssuesRotatableRefreshTokenCookieRevokedOnLogout() throws Exception {
        verifyEmail("remembertester@example.com");
        signup("remembertester", "remembertester@example.com", "리멤버테스터");

        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "loginId": "remembertester",
                                  "password": "correct-password1!",
                                  "rememberLogin": true
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.token").isString())
                .andReturn();

        Cookie refreshCookie = loginResult.getResponse().getCookie(REFRESH_COOKIE);
        assertNotNull(refreshCookie, "로그인 상태 유지 체크 시 리프레시 토큰 쿠키가 발급되어야 한다");
        assertTrue(refreshCookie.isHttpOnly());

        MvcResult refreshResult = mockMvc.perform(post("/api/auth/refresh").cookie(refreshCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.token").isString())
                .andReturn();

        Cookie rotatedCookie = refreshResult.getResponse().getCookie(REFRESH_COOKIE);
        assertNotNull(rotatedCookie, "리프레시 시 새 리프레시 토큰이 회전 발급되어야 한다");

        mockMvc.perform(post("/api/auth/logout").cookie(rotatedCookie))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/auth/refresh").cookie(rotatedCookie))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_INVALID_REFRESH_TOKEN"));
    }

    @Test
    void loginWithoutRememberLoginDoesNotIssueRefreshCookie() throws Exception {
        verifyEmail("norememtester@example.com");
        signup("norememtester", "norememtester@example.com", "노리멤버");

        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "loginId": "norememtester",
                                  "password": "correct-password1!",
                                  "rememberLogin": false
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn();

        assertNull(loginResult.getResponse().getCookie(REFRESH_COOKIE));
    }
}
