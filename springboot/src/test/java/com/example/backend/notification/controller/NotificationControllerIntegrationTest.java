package com.example.backend.notification.controller;

import com.example.backend.auth.domain.entity.Account;
import com.example.backend.auth.domain.entity.EmailVerification;
import com.example.backend.auth.repository.AccountRepository;
import com.example.backend.auth.repository.EmailVerificationRepository;
import com.example.backend.notification.domain.Notification;
import com.example.backend.notification.domain.NotificationType;
import com.example.backend.notification.repository.NotificationRepository;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class NotificationControllerIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired AccountRepository accountRepository;
    @Autowired EmailVerificationRepository emailVerificationRepository;
    @Autowired NotificationRepository notificationRepository;

    @Test
    void ownerCanListReadAndDeleteNotifications() throws Exception {
        signup("notifyuser", "notify@example.com", "알림사용자");
        String token = login("notifyuser");
        Account account = accountRepository.findByLoginId("notifyuser").orElseThrow();
        Notification notification = notificationRepository.save(new Notification(
                account,
                NotificationType.COMMENT,
                "새 댓글이 있습니다.",
                "POST",
                10L,
                "/pages/board/detail.html?postId=10"
        ));

        mockMvc.perform(get("/api/notifications").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].notificationId").value(notification.getNotificationId()))
                .andExpect(jsonPath("$.data[0].read").value(false));
        mockMvc.perform(get("/api/notifications/unread-count").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.count").value(1));

        mockMvc.perform(patch("/api/notifications/{id}/read", notification.getNotificationId())
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.read").value(true))
                .andExpect(jsonPath("$.data.readAt").isNotEmpty());

        notificationRepository.save(new Notification(
                account,
                NotificationType.POST_LIKE_MILESTONE,
                "추천 5개를 달성했습니다.",
                "POST",
                10L,
                "/pages/board/detail.html?postId=10"
        ));
        mockMvc.perform(patch("/api/notifications/read-all")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/notifications/unread-count").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.count").value(0));

        mockMvc.perform(delete("/api/notifications/{id}", notification.getNotificationId())
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk());
    }

    @Test
    void anotherAccountCannotReadOrDeleteNotification() throws Exception {
        signup("notifyowner", "notifyowner@example.com", "알림소유자");
        signup("notifyother", "notifyother@example.com", "다른사용자");
        String otherToken = login("notifyother");
        Account owner = accountRepository.findByLoginId("notifyowner").orElseThrow();
        Notification notification = notificationRepository.save(new Notification(
                owner,
                NotificationType.BUSINESS_APPROVED,
                "사업자 승인이 완료되었습니다.",
                "BUSINESS_APPLICATION",
                20L,
                "/pages/business/index.html"
        ));

        mockMvc.perform(patch("/api/notifications/{id}/read", notification.getNotificationId())
                        .header("Authorization", bearer(otherToken)))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/notifications/{id}", notification.getNotificationId())
                        .header("Authorization", bearer(otherToken)))
                .andExpect(status().isNotFound());
    }

    private void signup(String loginId, String email, String nickname) throws Exception {
        EmailVerification verification = new EmailVerification(
                email,
                "000000",
                LocalDateTime.now().plusMinutes(5)
        );
        verification.markVerified(LocalDateTime.now().plusMinutes(30));
        emailVerificationRepository.save(verification);
        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"loginId":"%s","password":"Correct1!","passwordConfirm":"Correct1!","email":"%s","nickname":"%s","ageConfirmed":true}
                                """.formatted(loginId, email, nickname)))
                .andExpect(status().isOk());
    }

    private String login(String loginId) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"loginId":"%s","password":"Correct1!","rememberLogin":false}
                                """.formatted(loginId)))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        return body.at("/data/token").asText();
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }
}
