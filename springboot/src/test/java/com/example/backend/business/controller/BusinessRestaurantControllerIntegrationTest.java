package com.example.backend.business.controller;

import com.example.backend.auth.domain.entity.Account;
import com.example.backend.auth.domain.entity.EmailVerification;
import com.example.backend.auth.domain.type.AuthorityCode;
import com.example.backend.auth.repository.AccountRepository;
import com.example.backend.auth.repository.EmailVerificationRepository;
import com.example.backend.auth.service.AuthorityService;
import jakarta.persistence.EntityManager;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
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

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class BusinessRestaurantControllerIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired AccountRepository accountRepository;
    @Autowired EmailVerificationRepository emailVerificationRepository;
    @Autowired AuthorityService authorityService;
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired EntityManager entityManager;

    @Test
    void businessCanManageOnlyOwnedRestaurants() throws Exception {
        Integer categoryId = createCategory();
        signup("regularuser", "regular@example.com", "일반회원");
        String regularToken = login("regularuser");
        mockMvc.perform(post("/api/business/restaurants")
                        .header("Authorization", bearer(regularToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createPayload("일반회원 식당", categoryId)))
                .andExpect(status().isForbidden());

        signup("owneruser", "owner@example.com", "사업자회원");
        Account owner = accountRepository.findByLoginId("owneruser").orElseThrow();
        authorityService.grant(owner.getAccountId(), AuthorityCode.ROLE_BUSINESS);
        String ownerToken = login("owneruser");

        mockMvc.perform(post("/api/business/restaurants")
                        .header("Authorization", bearer(ownerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createPayload("없는 카테고리 식당", 999999)))
                .andExpect(status().isNotFound());

        MvcResult createResult = mockMvc.perform(post("/api/business/restaurants")
                        .header("Authorization", bearer(ownerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createPayload("푸드덕 테스트 식당", categoryId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.name").value("푸드덕 테스트 식당"))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andReturn();
        long restaurantId = objectMapper.readTree(createResult.getResponse().getContentAsString())
                .at("/data/restaurantId").asLong();

        Long storedOwnerId = jdbcTemplate.queryForObject(
                "select owner_account_id from restaurant where restaurant_id = ?",
                Long.class,
                restaurantId
        );
        assertThat(storedOwnerId).isEqualTo(owner.getAccountId());
        mockMvc.perform(get("/api/business/restaurants")
                        .header("Authorization", bearer(ownerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].restaurantId").value(restaurantId));

        mockMvc.perform(put("/api/business/restaurants/{id}", restaurantId)
                        .header("Authorization", bearer(ownerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updatePayload("수정된 테스트 식당", categoryId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("수정된 테스트 식당"));
        mockMvc.perform(patch("/api/business/restaurants/{id}/status", restaurantId)
                        .header("Authorization", bearer(ownerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"INACTIVE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("INACTIVE"));
        entityManager.flush();

        mockMvc.perform(get("/api/public/restaurants/{id}", restaurantId))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/public/restaurants/{id}/menu", restaurantId))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/public/restaurants/{id}/reviews", restaurantId))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/public/restaurants/{id}/news", restaurantId))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/public/restaurants/{id}", restaurantId)
                        .header("Authorization", bearer(ownerToken)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/public/restaurants/{id}/menu", restaurantId)
                        .header("Authorization", bearer(ownerToken)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/public/restaurants/{id}/reviews", restaurantId)
                        .header("Authorization", bearer(ownerToken)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/public/restaurants/{id}/news", restaurantId)
                        .header("Authorization", bearer(ownerToken)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/restaurants/{id}/reviews", restaurantId)
                        .header("Authorization", bearer(regularToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rating\":5,\"content\":\"휴업 식당 리뷰\"}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/restaurants/{id}/favorite", restaurantId)
                        .header("Authorization", bearer(regularToken)))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/restaurants/{id}/news", restaurantId)
                        .header("Authorization", bearer(ownerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(newsPayload()))
                .andExpect(status().isOk());

        signup("otherowner", "otherowner@example.com", "다른사업자");
        Account otherOwner = accountRepository.findByLoginId("otherowner").orElseThrow();
        authorityService.grant(otherOwner.getAccountId(), AuthorityCode.ROLE_BUSINESS);
        String otherToken = login("otherowner");
        mockMvc.perform(get("/api/business/restaurants/{id}", restaurantId)
                        .header("Authorization", bearer(otherToken)))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/business/restaurants/{id}", restaurantId)
                        .header("Authorization", bearer(otherToken)))
                .andExpect(status().isNotFound());

        mockMvc.perform(delete("/api/business/restaurants/{id}", restaurantId)
                        .header("Authorization", bearer(ownerToken)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/public/restaurants/{id}", restaurantId)
                        .header("Authorization", bearer(ownerToken)))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/public/restaurants/{id}/menu", restaurantId)
                        .header("Authorization", bearer(ownerToken)))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/public/restaurants/{id}/reviews", restaurantId)
                        .header("Authorization", bearer(ownerToken)))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/public/restaurants/{id}/news", restaurantId)
                        .header("Authorization", bearer(ownerToken)))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/restaurants/{id}/news", restaurantId)
                        .header("Authorization", bearer(ownerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(newsPayload()))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/restaurants/{id}/favorite/toggle", restaurantId)
                        .header("Authorization", bearer(ownerToken)))
                .andExpect(status().isNotFound());

        entityManager.flush();
        String statusValue = jdbcTemplate.queryForObject(
                "select status from restaurant where restaurant_id = ?",
                String.class,
                restaurantId
        );
        assertThat(statusValue).isEqualTo("DELETED");
    }

    private Integer createCategory() {
        jdbcTemplate.update("""
                insert into restaurant_category (
                    category_code, name, display_order, active
                ) values ('TEST_KOREAN', '한식', 1, true)
                """);
        return jdbcTemplate.queryForObject(
                "select max(category_id) from restaurant_category",
                Integer.class
        );
    }

    private String createPayload(String name, Integer categoryId) {
        return """
                {"name":"%s","address":"서울시 테스트로 1","addressDetail":"2층","categoryId":%d,"phone":"02-1234-5678","openingHours":"11:00-22:00","closedDays":"월요일","description":"테스트 식당","latitude":37.1234567,"longitude":127.1234567}
                """.formatted(name, categoryId);
    }

    private String updatePayload(String name, Integer categoryId) {
        return """
                {"name":"%s","address":"서울시 테스트로 2","addressDetail":"3층","categoryId":%d,"phone":"02-9876-5432","openingHours":"10:00-21:00","closedDays":"화요일","description":"수정된 식당","latitude":37.7654321,"longitude":127.7654321}
                """.formatted(name, categoryId);
    }

    private String newsPayload() {
        return """
                {"title":"임시 휴업 안내","content":"재개 준비 중입니다."}
                """;
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
