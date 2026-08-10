package com.example.backend.preset.controller;

import com.example.backend.global.security.jwt.JwtProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class PresetControllerIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private JwtProvider jwtProvider;

    private Long presetId;
    private Long restaurantId;
    private Long accountId;
    private String accessToken;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("""
                insert into account (
                    login_id, email, nickname, gender, email_verified,
                    profile_completed, status, created_at, updated_at
                ) values (?, ?, ?, ?, ?, ?, ?, current_timestamp, current_timestamp)
                """,
                "preset-owner", "preset-owner@example.com", "프리셋점주",
                "UNSPECIFIED", true, true, "ACTIVE");
        accountId = jdbcTemplate.queryForObject("select max(account_id) from account", Long.class);

        jdbcTemplate.update("""
                insert into preset (
                    title, category,
                    view_count, display_order, status, account_id, is_public
                ) values (?, ?, ?, ?, ?, ?, ?)
                """,
                "성수 데이트 맛집", "데이트", 12, 1, "ACTIVE", accountId, true);
        presetId = jdbcTemplate.queryForObject("select max(preset_id) from preset", Long.class);

        jdbcTemplate.update("""
                insert into restaurant (
                    owner_account_id, name, address, address_detail,
                    latitude, longitude, phone, opening_hours, closed_days,
                    description, status, created_at, updated_at
                ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, current_timestamp, current_timestamp)
                """,
                accountId, "성수 테스트 키친", "서울 성동구 테스트로 1", "1층",
                37.5445, 127.0560, "02-0000-0000", "11:00-21:00", "월요일",
                "프리셋 조회 테스트용 음식점", "ACTIVE");
        restaurantId = jdbcTemplate.queryForObject("select max(restaurant_id) from restaurant", Long.class);
        jdbcTemplate.update("""
                insert into preset_restaurant (
                    preset_id, restaurant_id, display_order, description
                ) values (?, ?, ?, ?)
                """, presetId, restaurantId, 1, "데이트 분위기가 좋은 곳");

        jdbcTemplate.update("insert into tag (name) values (?)", "데이트");
        Integer tagId = jdbcTemplate.queryForObject("select max(tag_id) from tag", Integer.class);
        jdbcTemplate.update(
                "insert into preset_tag (preset_id, tag_id, display_order) values (?, ?, ?)",
                presetId, tagId, 1);
        accessToken = jwtProvider.createAccessToken(accountId, "preset-owner", List.of("ROLE_USER"));
    }

    @Test
    @DisplayName("로그인 사용자는 계정 소유권과 공개 여부를 포함해 Presset을 등록한다")
    void createsPresetForAuthenticatedAccount() throws Exception {
        MockMultipartFile data = new MockMultipartFile(
                "data", "data", MediaType.APPLICATION_JSON_VALUE, """
                {
                  "title": "비공개 회식 후보",
                  "category": "회식",
                  "isPublic": false
                }
                """.getBytes(StandardCharsets.UTF_8)
        );
        mockMvc.perform(multipart("/api/presets")
                        .file(data)
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.message").value("프리셋을 등록했습니다."))
                .andExpect(jsonPath("$.data").isNumber());

        Long createdId = jdbcTemplate.queryForObject(
                "select preset_id from preset where title = ?",
                Long.class,
                "비공개 회식 후보"
        );
        assertEquals(accountId, jdbcTemplate.queryForObject(
                "select account_id from preset where preset_id = ?", Long.class, createdId));
        assertEquals(Boolean.FALSE, jdbcTemplate.queryForObject(
                "select is_public from preset where preset_id = ?", Boolean.class, createdId));

        mockMvc.perform(get("/api/presets/{presetId}", createdId))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/presets/{presetId}", createdId)
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.presetId").value(createdId));
    }

    @Test
    @DisplayName("Presset 등록 요청의 필수값을 서버에서도 검증한다")
    void validatesPresetCreateRequest() throws Exception {
        MockMultipartFile data = new MockMultipartFile(
                "data", "data", MediaType.APPLICATION_JSON_VALUE,
                """
                {"title":" ","category":" "}
                """.getBytes(StandardCharsets.UTF_8)
        );
        mockMvc.perform(multipart("/api/presets")
                        .file(data)
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.data.title").exists())
                .andExpect(jsonPath("$.data.category").exists());
    }

    @Test
    @DisplayName("비회원도 태그가 포함된 활성 Presset 페이지를 조회한다")
    void anonymousGetsPresetList() throws Exception {
        mockMvc.perform(get("/api/presets"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].presetId").value(presetId))
                .andExpect(jsonPath("$.data.content[0].restaurantCount").value(1))
                .andExpect(jsonPath("$.data.content[0].tags[0].tagName").value("데이트"));
    }

    @Test
    @DisplayName("상세 조회는 조회 수를 원자적으로 증가시키고 음식점을 반환한다")
    void getsPresetDetail() throws Exception {
        mockMvc.perform(get("/api/presets/{presetId}", presetId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.presetId").value(presetId))
                .andExpect(jsonPath("$.data.viewCount").value(13))
                .andExpect(jsonPath("$.data.restaurants[0].restaurantId").value(restaurantId))
                .andExpect(jsonPath("$.data.restaurants[0].presetDescription").value("데이트 분위기가 좋은 곳"));
    }

    @Test
    @DisplayName("Presset 지도는 상세와 같은 음식점 ID와 좌표를 반환한다")
    void getsPresetMapRestaurants() throws Exception {
        mockMvc.perform(get("/api/presets/{presetId}/map-restaurants", presetId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.restaurants[0].restaurantId").value(restaurantId))
                .andExpect(jsonPath("$.data.restaurants[0].coordinateAvailable").value(true));
    }

    @Test
    @DisplayName("로그인 사용자는 Presset과 음식점 저장을 중복 없이 추가하고 해제한다")
    void togglesFavoritesIdempotently() throws Exception {
        mockMvc.perform(post("/api/presets/{presetId}/favorite", presetId)
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.favoriteCount").value(1));
        mockMvc.perform(post("/api/presets/{presetId}/favorite", presetId)
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.favoriteCount").value(1));
        mockMvc.perform(post("/api/restaurants/{restaurantId}/favorite", restaurantId)
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.favoriteByCurrentUser").value(true));
        mockMvc.perform(delete("/api/presets/{presetId}/favorite", presetId)
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.favoriteCount").value(0));
    }

    @Test
    @DisplayName("비회원 저장과 일반 사용자의 관리자 API 호출은 거부된다")
    void protectsFavoriteAndAdminApis() throws Exception {
        mockMvc.perform(post("/api/presets/{presetId}/favorite", presetId))
                .andExpect(status().isUnauthorized());
        MockMultipartFile data = new MockMultipartFile(
                "data", "data", MediaType.APPLICATION_JSON_VALUE,
                """
                {"title":"테스트","category":"기타"}
                """.getBytes(StandardCharsets.UTF_8)
        );
        mockMvc.perform(multipart("/api/presets").file(data))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/admin/presets")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("존재하지 않는 Presset은 404를 반환한다")
    void returnsNotFoundForMissingPreset() throws Exception {
        mockMvc.perform(get("/api/presets/{presetId}", Long.MAX_VALUE))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("COMMON_RESOURCE_NOT_FOUND"));
    }

    @Test
    @DisplayName("Presset 목록·상세·관리 정적 페이지를 제공한다")
    void servesPressetPages() throws Exception {
        mockMvc.perform(get("/pages/presset/index.html"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("preset-create-form")))
                .andExpect(content().string(containsString("result-message")))
                .andExpect(content().string(containsString("preset-list")));
        mockMvc.perform(get("/pages/presset/detail.html"))
                .andExpect(status().isOk()).andExpect(content().string(containsString("preset-detail")));
        mockMvc.perform(get("/pages/admin/presets.html"))
                .andExpect(status().isOk()).andExpect(content().string(containsString("preset-admin-dashboard")));
    }
}
