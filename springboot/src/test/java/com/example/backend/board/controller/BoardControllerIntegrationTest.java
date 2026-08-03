package com.example.backend.board.controller;

import com.example.backend.auth.domain.entity.EmailVerification;
import com.example.backend.auth.dto.request.LoginRequest;
import com.example.backend.auth.dto.request.SignupRequest;
import com.example.backend.auth.repository.EmailVerificationRepository;
import com.example.backend.auth.service.AuthService;
import com.example.backend.auth.dto.response.AuthTokenResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class BoardControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AuthService authService;

    @Autowired
    private EmailVerificationRepository emailVerificationRepository;

    private String accessToken;

    @BeforeEach
    void setUp() {
        EmailVerification verification = new EmailVerification(
                "boardtester@example.com", "000000", LocalDateTime.now().plusMinutes(5)
        );
        verification.markVerified(LocalDateTime.now().plusMinutes(30));
        emailVerificationRepository.save(verification);

        authService.signup(new SignupRequest(
                "boardtester",
                "correct-password",
                "correct-password",
                "boardtester@example.com",
                "게시판테스터"
        ));
        AuthTokenResponse token = authService.login(
                new LoginRequest("boardtester", "correct-password", false)
        ).accessToken();
        accessToken = token.token();
    }

    @Test
    @DisplayName("커뮤니티 목록·글쓰기·상세 정적 페이지를 제공한다")
    void servesDedicatedCommunityPages() throws Exception {
        mockMvc.perform(get("/pages/board/index.html"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("data-write-link")));

        mockMvc.perform(get("/pages/board/write.html"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("post-editor-form")));

        mockMvc.perform(get("/pages/board/detail.html"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("post-detail-content")));
    }

    @Test
    @DisplayName("비회원은 일반 목록을 읽지만 글쓰기는 401이다")
    void anonymousReadsGeneralButCannotWrite() throws Exception {
        mockMvc.perform(get("/api/board/posts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content").isArray());

        mockMvc.perform(post("/api/board/posts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "boardType": "GENERAL",
                                  "category": "QUESTION",
                                  "title": "로그인 없는 글",
                                  "content": "작성되면 안 됩니다."
                                }
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_UNAUTHORIZED"));
    }

    @Test
    @DisplayName("일반 회원은 JWT로 일반 글을 작성하고 BUSINESS는 볼 수 없다")
    void userWritesGeneralButCannotReadBusiness() throws Exception {
        mockMvc.perform(post("/api/board/posts")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "boardType": "GENERAL",
                                  "category": "QUESTION",
                                  "title": "강남 혼밥 질문",
                                  "content": "조용한 식당을 찾습니다."
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.title").value("강남 혼밥 질문"))
                .andExpect(jsonPath("$.data.authorLoginId").value("boardtester"))
                .andExpect(jsonPath("$.data.authorRole").value("USER"))
                .andExpect(jsonPath("$.data.ownedByCurrentUser").value(true));

        mockMvc.perform(get("/api/board/posts")
                        .header("Authorization", "Bearer " + accessToken)
                        .param("boardType", "BUSINESS"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("BOARD_BUSINESS_READ_FORBIDDEN"));
    }
}
