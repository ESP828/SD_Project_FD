package com.example.backend.review.controller;

import com.example.backend.auth.domain.entity.Account;
import com.example.backend.auth.domain.entity.EmailVerification;
import com.example.backend.auth.repository.AccountRepository;
import com.example.backend.auth.repository.EmailVerificationRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ReviewManagementControllerIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired AccountRepository accountRepository;
    @Autowired EmailVerificationRepository emailVerificationRepository;
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired EntityManager entityManager;

    @Test
    void onlyOwnerCanUpdateAndSoftDeleteReviewAndCanWriteAgainAfterDeletion() throws Exception {
        signup("reviewowner", "reviewowner@example.com", "리뷰소유자");
        signup("reviewother", "reviewother@example.com", "리뷰타인");
        String ownerToken = login("reviewowner");
        String otherToken = login("reviewother");
        Account owner = accountRepository.findByLoginId("reviewowner").orElseThrow();
        long restaurantId = createRestaurant(owner.getAccountId(), "리뷰 관리 식당");

        MvcResult createResult = mockMvc.perform(post("/api/restaurants/{id}/reviews", restaurantId)
                        .header("Authorization", bearer(ownerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rating\":5,\"content\":\"처음 리뷰\"}"))
                .andExpect(status().isOk())
                .andReturn();
        long reviewId = objectMapper.readTree(createResult.getResponse().getContentAsString())
                .at("/data/reviewId").asLong();

        mockMvc.perform(put("/api/reviews/{id}", reviewId)
                        .header("Authorization", bearer(ownerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rating\":6,\"content\":\"범위 밖\"}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(put("/api/reviews/{id}", reviewId)
                        .header("Authorization", bearer(otherToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rating\":1,\"content\":\"타인 수정\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("REVIEW_NOT_FOUND"));

        mockMvc.perform(put("/api/reviews/{id}", reviewId)
                        .header("Authorization", bearer(ownerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rating\":3,\"content\":\"수정 리뷰\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.rating").value(3))
                .andExpect(jsonPath("$.data.content").value("수정 리뷰"));

        mockMvc.perform(delete("/api/reviews/{id}", reviewId)
                        .header("Authorization", bearer(ownerToken)))
                .andExpect(status().isOk());
        entityManager.flush();

        String storedStatus = jdbcTemplate.queryForObject(
                "select status from review where review_id = ?",
                String.class,
                reviewId
        );
        LocalDateTime deletedAt = jdbcTemplate.queryForObject(
                "select deleted_at from review where review_id = ?",
                LocalDateTime.class,
                reviewId
        );
        assertEquals("DELETED", storedStatus);
        assertNotNull(deletedAt);

        mockMvc.perform(delete("/api/reviews/{id}", reviewId)
                        .header("Authorization", bearer(ownerToken)))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/public/restaurants/{id}/reviews", restaurantId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isEmpty());
        mockMvc.perform(get("/api/mypage/reviews")
                        .header("Authorization", bearer(ownerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isEmpty());

        MvcResult secondCreate = mockMvc.perform(post("/api/restaurants/{id}/reviews", restaurantId)
                        .header("Authorization", bearer(ownerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rating\":4,\"content\":\"다시 작성\"}"))
                .andExpect(status().isOk())
                .andReturn();
        long secondReviewId = objectMapper.readTree(secondCreate.getResponse().getContentAsString())
                .at("/data/reviewId").asLong();
        assertNull(jdbcTemplate.queryForObject(
                "select deleted_at from review where review_id = ?",
                LocalDateTime.class,
                secondReviewId
        ));
    }

    @Test
    void publicRestaurantReviewUsesSameManagementApi() throws Exception {
        signup("publicreviewer", "publicreviewer@example.com", "공공리뷰어");
        String token = login("publicreviewer");
        long publicRestaurantId = createPublicRestaurant();

        MvcResult createResult = mockMvc.perform(post("/api/map/restaurants/{id}/reviews", publicRestaurantId)
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rating\":5,\"content\":\"공공 리뷰\"}"))
                .andExpect(status().isOk())
                .andReturn();
        long reviewId = objectMapper.readTree(createResult.getResponse().getContentAsString())
                .at("/data/reviewId").asLong();

        mockMvc.perform(put("/api/reviews/{id}", reviewId)
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rating\":4,\"content\":\"수정한 공공 리뷰\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.rating").value(4));
        mockMvc.perform(delete("/api/reviews/{id}", reviewId)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk());
    }

    private long createRestaurant(long ownerAccountId, String name) {
        jdbcTemplate.update("""
                insert into restaurant (
                    owner_account_id, name, address, status, created_at, updated_at
                ) values (?, ?, ?, 'ACTIVE', current_timestamp, current_timestamp)
                """, ownerAccountId, name, "서울시 리뷰로 1");
        return jdbcTemplate.queryForObject(
                "select restaurant_id from restaurant where name = ?",
                Long.class,
                name
        );
    }

    private long createPublicRestaurant() {
        jdbcTemplate.update(
                "insert into public_restaurant (external_store_id, name) values (?, ?)",
                "REVIEW_PUBLIC_STORE",
                "공공 리뷰 식당"
        );
        return jdbcTemplate.queryForObject(
                "select public_restaurant_id from public_restaurant where external_store_id = ?",
                Long.class,
                "REVIEW_PUBLIC_STORE"
        );
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
                                {"loginId":"%s","password":"Correct1!","passwordConfirm":"Correct1!","email":"%s","nickname":"%s"}
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
