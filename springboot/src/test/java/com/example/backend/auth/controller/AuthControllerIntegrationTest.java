package com.example.backend.auth.controller;

import com.example.backend.auth.dto.response.AuthTokenResponse;
import com.example.backend.auth.service.OAuthLoginTicketService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AuthControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private OAuthLoginTicketService ticketService;

    @Test
    void signupLoginAndPublicMapConfigUseCommonResponseContract() throws Exception {
        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "loginId": "apitester",
                                  "password": "correct-password",
                                  "email": "apitester@example.com",
                                  "nickname": "API테스터"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value("SUCCESS"));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "loginId": "apitester",
                                  "password": "correct-password"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.token").isString())
                .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.data.expiresIn").value(1_800_000));

        mockMvc.perform(get("/api/public/map/config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.provider").value("KAKAO"))
                .andExpect(jsonPath("$.data.javascriptKey").value("test-public-map-key"))
                .andExpect(jsonPath("$.data.configured").value(true));
    }

    @Test
    void socialLoginEntryUsesSignedStateAndLegacyCompatiblePath() throws Exception {
        mockMvc.perform(get("/api/auth/kakao/login"))
                .andExpect(status().isFound())
                .andExpect(header().string(
                        "Location",
                        containsString("https://kauth.kakao.com/oauth/authorize")
                ))
                .andExpect(header().string("Location", containsString("state=")))
                .andExpect(header().string("Set-Cookie", containsString("HttpOnly")))
                .andExpect(header().string("Set-Cookie", containsString("SameSite=Lax")));

        mockMvc.perform(get("/api/auth/oauth/google/login"))
                .andExpect(status().isFound())
                .andExpect(header().string(
                        "Location",
                        containsString("https://accounts.google.com/o/oauth2/v2/auth")
                ))
                .andExpect(header().string("Location", containsString("state=")));
    }

    @Test
    void missingPublicResourceReturns404InsteadOf500() throws Exception {
        mockMvc.perform(get("/favicon.svg"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/favicon.ico"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("COMMON_RESOURCE_NOT_FOUND"));
    }

    @Test
    void oauthExchangeTicketIsSingleUse() throws Exception {
        String ticket = ticketService.issue(
                AuthTokenResponse.bearer("one-time-token", 1_800_000)
        );
        String requestBody = "{\"ticket\":\"" + ticket + "\"}";

        mockMvc.perform(post("/api/auth/oauth/exchange")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.token").value("one-time-token"));

        mockMvc.perform(post("/api/auth/oauth/exchange")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("AUTH_INVALID_OAUTH_TICKET"));
    }

    @Test
    void protectedUnknownApiRejectsAnonymousRequestBeforeRouting() throws Exception {
        mockMvc.perform(get("/api/mypage/not-yet-implemented"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_UNAUTHORIZED"));
    }
}
