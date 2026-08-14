package com.example.backend.business.controller;

import com.example.backend.auth.domain.entity.Account;
import com.example.backend.auth.domain.entity.EmailVerification;
import com.example.backend.auth.domain.type.AuthorityCode;
import com.example.backend.auth.repository.AccountRepository;
import com.example.backend.auth.repository.EmailVerificationRepository;
import com.example.backend.auth.service.AuthorityService;
import com.example.backend.business.repository.BusinessProfileRepository;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class BusinessApplicationControllerIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired AccountRepository accountRepository;
    @Autowired EmailVerificationRepository emailVerificationRepository;
    @Autowired AuthorityService authorityService;
    @Autowired BusinessProfileRepository businessProfileRepository;
    @Autowired NotificationRepository notificationRepository;

    @Test
    void applicantCanSubmitAndViewOwnApplications() throws Exception {
        signup("bizuser", "Correct1!", "biz@example.com", "사업자유저");
        String token = login("bizuser", "Correct1!");

        submitApplication(token, "푸드덕 테스트점")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.businessName").value("푸드덕 테스트점"));

        mockMvc.perform(get("/api/business/applications").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].status").value("PENDING"));

        submitApplication(token, "중복 신청점")
                .andExpect(status().isConflict());
    }

    @Test
    void adminCanListAndApproveApplications() throws Exception {
        signup("applicant", "Correct1!", "applicant@example.com", "신청자");
        String applicantToken = login("applicant", "Correct1!");
        MvcResult applicationResult = submitApplication(applicantToken, "관리자 승인 테스트점")
                .andExpect(status().isOk()).andReturn();
        long applicationId = objectMapper.readTree(applicationResult.getResponse().getContentAsString())
                .at("/data/applicationId").asLong();
        Account applicant = accountRepository.findByLoginId("applicant").orElseThrow();

        signup("adminuser", "Correct1!", "admin@example.com", "관리자");
        authorityService.grant(accountRepository.findByLoginId("adminuser").orElseThrow().getAccountId(), AuthorityCode.ROLE_ADMIN);
        String adminToken = login("adminuser", "Correct1!");

        mockMvc.perform(get("/api/admin/business-applications").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].status").value("PENDING"));
        mockMvc.perform(patch("/api/admin/business-applications/{id}/approve", applicationId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("APPROVED"));

        assertThat(authorityService.findCodes(applicant.getAccountId())).contains("ROLE_BUSINESS");
        assertThat(businessProfileRepository.existsByAccountAccountId(applicant.getAccountId())).isTrue();
        assertThat(notificationRepository.existsByAccountAccountIdAndType(
                applicant.getAccountId(),
                NotificationType.BUSINESS_APPROVED
        )).isTrue();
    }

    @Test
    void rejectionCreatesNotificationWithoutGrantingBusinessRole() throws Exception {
        signup("rejected", "Correct1!", "rejected@example.com", "반려신청자");
        String applicantToken = login("rejected", "Correct1!");
        MvcResult applicationResult = submitApplication(applicantToken, "반려 테스트점")
                .andExpect(status().isOk()).andReturn();
        long applicationId = objectMapper.readTree(applicationResult.getResponse().getContentAsString())
                .at("/data/applicationId").asLong();
        Account applicant = accountRepository.findByLoginId("rejected").orElseThrow();

        signup("rejectadmin", "Correct1!", "rejectadmin@example.com", "반려관리자");
        Long adminId = accountRepository.findByLoginId("rejectadmin").orElseThrow().getAccountId();
        authorityService.grant(adminId, AuthorityCode.ROLE_ADMIN);
        String adminToken = login("rejectadmin", "Correct1!");

        mockMvc.perform(patch("/api/admin/business-applications/{id}/reject", applicationId)
                        .header("Authorization", "Bearer " + adminToken)
                        .param("rejectReason", "제출 정보를 다시 확인해 주세요."))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("REJECTED"));

        assertThat(authorityService.findCodes(applicant.getAccountId())).doesNotContain("ROLE_BUSINESS");
        assertThat(notificationRepository.existsByAccountAccountIdAndType(
                applicant.getAccountId(),
                NotificationType.BUSINESS_REJECTED
        )).isTrue();
    }

    private org.springframework.test.web.servlet.ResultActions submitApplication(String token, String name) throws Exception {
        return mockMvc.perform(post("/api/business/applications")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"businessName":"%s","businessNumber":"123-45-67891","representativeName":"홍길동","openedAt":"2020-01-01","contact":"010-1234-5678","reason":"테스트 신청"}
                        """.formatted(name)));
    }

    private void signup(String loginId, String password, String email, String nickname) throws Exception {
        EmailVerification verification = new EmailVerification(email, "000000", LocalDateTime.now().plusMinutes(5));
        verification.markVerified(LocalDateTime.now().plusMinutes(30));
        emailVerificationRepository.save(verification);
        mockMvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON).content("""
                {"loginId":"%s","password":"%s","passwordConfirm":"%s","email":"%s","nickname":"%s"}
                """.formatted(loginId, password, password, email, nickname))).andExpect(status().isOk());
    }

    private String login(String loginId, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("""
                {"loginId":"%s","password":"%s","rememberLogin":false}
                """.formatted(loginId, password))).andExpect(status().isOk()).andReturn();
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        return body.at("/data/token").asText();
    }
}
