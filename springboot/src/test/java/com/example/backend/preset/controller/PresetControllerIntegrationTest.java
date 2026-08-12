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
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.transaction.AfterTransaction;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
@TestPropertySource(properties = "app.upload.preset-image-dir=target/test-uploads/preset-images")
class PresetControllerIntegrationTest {

    private static final Path TEST_IMAGE_DIR = Path.of(
            "target", "test-uploads", "preset-images"
    ).toAbsolutePath().normalize();

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private JwtProvider jwtProvider;

    private Long presetId;
    private Long restaurantId;
    private Long ownerRestaurantId;
    private Long accountId;
    private String accessToken;
    private Path rolledBackImagePath;

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

        restaurantId = createRestaurant("성수 테스트 키친");
        jdbcTemplate.update(
                "update public_restaurant set latitude = ?, longitude = ? where public_restaurant_id = ?",
                37.5445, 127.0560, restaurantId);
        jdbcTemplate.update("""
                insert into preset_restaurant (
                    preset_id, public_restaurant_id, display_order, description
                ) values (?, ?, ?, ?)
                """, presetId, restaurantId, 1, "데이트 분위기가 좋은 곳");

        jdbcTemplate.update("""
                insert into restaurant (
                    owner_account_id, name, address, address_detail,
                    latitude, longitude, phone, opening_hours, closed_days,
                    description, status, created_at, updated_at
                ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, current_timestamp, current_timestamp)
                """,
                accountId, "성수 사장님 매장", "서울 성동구 테스트로 1", "1층",
                37.5445, 127.0560, "02-0000-0000", "11:00-21:00", "월요일",
                "찜 기능 테스트용 음식점", "ACTIVE");
        ownerRestaurantId = jdbcTemplate.queryForObject("select max(restaurant_id) from restaurant", Long.class);

        jdbcTemplate.update("insert into tag (name) values (?)", "데이트");
        Integer tagId = jdbcTemplate.queryForObject("select max(tag_id) from tag", Integer.class);
        jdbcTemplate.update(
                "insert into preset_tag (preset_id, tag_id, display_order) values (?, ?, ?)",
                presetId, tagId, 1);
        accessToken = jwtProvider.createAccessToken(accountId, "preset-owner", List.of("ROLE_USER"));
    }

    @AfterTransaction
    void confirmsRolledBackImageFileIsRemoved() throws Exception {
        if (rolledBackImagePath == null) {
            return;
        }
        boolean fileStillExists = Files.exists(rolledBackImagePath);
        Files.deleteIfExists(rolledBackImagePath);
        assertFalse(fileStillExists, "DB 트랜잭션 롤백 시 새 이미지 파일도 제거되어야 합니다.");
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
        assertEquals(0, jdbcTemplate.queryForObject(
                "select count(*) from preset_image where preset_id = ?", Integer.class, createdId));
        assertNull(jdbcTemplate.queryForObject(
                "select preset_image_id from preset where preset_id = ?", Long.class, createdId));

        mockMvc.perform(get("/api/presets/{presetId}", createdId))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/presets/{presetId}", createdId)
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.presetId").value(createdId))
                .andExpect(jsonPath("$.data.imageUrl").doesNotExist());
    }

    @Test
    @DisplayName("이미지가 있는 Presset은 파일과 preset_image 메타데이터를 같은 PK로 저장한다")
    void storesPresetImageMetadataForCreatedPreset() throws Exception {
        MockMultipartFile data = new MockMultipartFile(
                "data", "data", MediaType.APPLICATION_JSON_VALUE, """
                {
                  "title": "이미지 포함 데이트 코스",
                  "category": "데이트",
                  "isPublic": false
                }
                """.getBytes(StandardCharsets.UTF_8)
        );
        byte[] imageBytes = Base64.getDecoder().decode(
                "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAusB9Wl2n1cAAAAASUVORK5CYII="
        );
        MockMultipartFile image = new MockMultipartFile(
                "image", "cover.png", MediaType.IMAGE_PNG_VALUE, imageBytes
        );

        mockMvc.perform(multipart("/api/presets")
                        .file(data)
                        .file(image)
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data").isNumber());

        Long createdId = jdbcTemplate.queryForObject(
                "select preset_id from preset where title = ?",
                Long.class,
                "이미지 포함 데이트 코스"
        );
        Map<String, Object> metadata = jdbcTemplate.queryForMap("""
                select preset_image_id, preset_id, stored_filename, original_filename,
                       content_type, file_size, created_at
                  from preset_image
                 where preset_id = ?
                """, createdId);

        Long presetImageId = ((Number) metadata.get("preset_image_id")).longValue();
        assertEquals(createdId, ((Number) metadata.get("preset_id")).longValue());
        assertEquals(presetImageId, jdbcTemplate.queryForObject(
                "select preset_image_id from preset where preset_id = ?", Long.class, createdId));
        assertEquals(1, jdbcTemplate.queryForObject("""
                select count(*)
                  from preset p
                  join preset_image pi
                    on pi.preset_image_id = p.preset_image_id
                   and pi.preset_id = p.preset_id
                 where p.preset_id = ?
                """, Integer.class, createdId));
        assertEquals("cover.png", metadata.get("original_filename"));
        assertEquals(MediaType.IMAGE_PNG_VALUE, metadata.get("content_type"));
        assertEquals(imageBytes.length, ((Number) metadata.get("file_size")).longValue());
        assertNotNull(metadata.get("created_at"));

        String storedFilename = (String) metadata.get("stored_filename");
        assertTrue(storedFilename.matches("[0-9a-f]{32}\\.png"));
        rolledBackImagePath = TEST_IMAGE_DIR.resolve(storedFilename).normalize();
        assertTrue(rolledBackImagePath.startsWith(TEST_IMAGE_DIR));
        assertTrue(Files.exists(rolledBackImagePath));
        assertEquals(imageBytes.length, Files.size(rolledBackImagePath));

        String imageUrl = "/uploads/preset-images/" + storedFilename;
        mockMvc.perform(get("/api/presets/{presetId}", createdId)
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.imageUrl").value(imageUrl));
        mockMvc.perform(get("/api/presets")
                        .param("sort", "latest")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].presetId").value(createdId))
                .andExpect(jsonPath("$.data.content[0].imageUrl").value(imageUrl));
        mockMvc.perform(get(imageUrl))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.IMAGE_PNG))
                .andExpect(content().bytes(imageBytes));
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
                .andExpect(jsonPath("$.data.restaurantCount").value(1))
                .andExpect(jsonPath("$.data.restaurantLimit").value(15))
                .andExpect(jsonPath("$.data.restaurantOptions").isArray())
                .andExpect(jsonPath("$.data.restaurants[0].restaurantId").value(restaurantId))
                .andExpect(jsonPath("$.data.restaurants[0].presetDescription").value("데이트 분위기가 좋은 곳"));
    }

    @Test
    @DisplayName("Presset별 최대 15개와 중복을 막고 삭제 후 다시 추가한다")
    void managesRestaurantsWithinPerPresetLimit() throws Exception {
        Long limitedPresetId = createPreset("15개 제한 Presset", accountId);
        List<Long> restaurants = new ArrayList<>();
        for (int index = 1; index <= 16; index++) {
            restaurants.add(createRestaurant("제한 테스트 맛집 " + index));
        }
        for (int index = 0; index < 14; index++) {
            connectRestaurant(limitedPresetId, restaurants.get(index), index);
        }

        mockMvc.perform(post(
                        "/api/presets/{presetId}/restaurants/{restaurantId}",
                        limitedPresetId,
                        restaurants.get(14)
                ).header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.restaurantCount").value(15))
                .andExpect(jsonPath("$.data.restaurantLimit").value(15));

        mockMvc.perform(post(
                        "/api/presets/{presetId}/restaurants/{restaurantId}",
                        limitedPresetId,
                        restaurants.get(15)
                ).header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PRESET_RESTAURANT_LIMIT_EXCEEDED"))
                .andExpect(jsonPath("$.message").value("한 프리셋에는 최대 15개의 맛집만 담을 수 있습니다."));

        mockMvc.perform(post(
                        "/api/presets/{presetId}/restaurants/{restaurantId}",
                        limitedPresetId,
                        restaurants.get(14)
                ).header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PRESET_RESTAURANT_DUPLICATE"));

        mockMvc.perform(delete(
                        "/api/presets/{presetId}/restaurants/{restaurantId}",
                        limitedPresetId,
                        restaurants.get(0)
                ).header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.restaurantCount").value(14));
        mockMvc.perform(post(
                        "/api/presets/{presetId}/restaurants/{restaurantId}",
                        limitedPresetId,
                        restaurants.get(15)
                ).header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.restaurantCount").value(15));

        assertEquals(15, countPresetRestaurants(limitedPresetId));
        mockMvc.perform(get("/api/presets/{presetId}", limitedPresetId)
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.restaurantCount").value(15))
                .andExpect(jsonPath("$.data.restaurants.length()").value(15));

        Long independentPresetId = createPreset("독립 제한 Presset", accountId);
        for (int index = 0; index < 5; index++) {
            connectRestaurant(independentPresetId, restaurants.get(index), index);
        }
        mockMvc.perform(post(
                        "/api/presets/{presetId}/restaurants/{restaurantId}",
                        independentPresetId,
                        restaurants.get(5)
                ).header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.restaurantCount").value(6));
        assertEquals(6, countPresetRestaurants(independentPresetId));
        assertEquals(15, countPresetRestaurants(limitedPresetId));
    }

    @Test
    @DisplayName("Presset 맛집 변경은 작성자에게만 허용한다")
    void protectsPresetRestaurantMutationByOwner() throws Exception {
        jdbcTemplate.update("""
                insert into account (
                    login_id, email, nickname, gender, email_verified,
                    profile_completed, status, created_at, updated_at
                ) values (?, ?, ?, ?, ?, ?, ?, current_timestamp, current_timestamp)
                """,
                "preset-other", "preset-other@example.com", "다른사용자",
                "UNSPECIFIED", true, true, "ACTIVE");
        Long otherAccountId = jdbcTemplate.queryForObject(
                "select max(account_id) from account", Long.class);
        String otherToken = jwtProvider.createAccessToken(
                otherAccountId, "preset-other", List.of("ROLE_USER"));
        Long candidateId = createRestaurant("권한 확인 맛집");

        mockMvc.perform(post(
                        "/api/presets/{presetId}/restaurants/{restaurantId}",
                        presetId,
                        candidateId
                ).header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PRESET_OWNER_REQUIRED"));
        mockMvc.perform(post(
                "/api/presets/{presetId}/restaurants/{restaurantId}", presetId, candidateId))
                .andExpect(status().isUnauthorized());
        assertEquals(1, countPresetRestaurants(presetId));
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
        mockMvc.perform(post("/api/restaurants/{restaurantId}/favorite", ownerRestaurantId)
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
    @DisplayName("Presset 목록·등록·상세·관리 정적 페이지를 제공한다")
    void servesPressetPages() throws Exception {
        mockMvc.perform(get("/pages/presset/index.html"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("/pages/presset/register.html")))
                .andExpect(content().string(not(containsString("preset-create-form"))))
                .andExpect(content().string(containsString("preset-list")));
        mockMvc.perform(get("/pages/presset/register.html"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("preset-create-form")))
                .andExpect(content().string(containsString("preset-image-preview")))
                .andExpect(content().string(containsString("/pages/presset/register.js")));
        mockMvc.perform(get("/pages/presset/detail.html"))
                .andExpect(status().isOk()).andExpect(content().string(containsString("preset-detail")));
        mockMvc.perform(get("/pages/admin/presets.html"))
                .andExpect(status().isOk()).andExpect(content().string(containsString("preset-admin-dashboard")));
    }

    private Long createPreset(String title, Long ownerAccountId) {
        jdbcTemplate.update("""
                insert into preset (
                    title, category, view_count, display_order,
                    status, account_id, is_public
                ) values (?, ?, 0, 0, 'ACTIVE', ?, true)
                """, title, "테스트", ownerAccountId);
        return jdbcTemplate.queryForObject("select max(preset_id) from preset", Long.class);
    }

    private Long createRestaurant(String name) {
        jdbcTemplate.update("""
                insert into public_restaurant (
                    external_store_id, name, road_address, status, created_at, updated_at
                ) values (?, ?, ?, 'ACTIVE', current_timestamp, current_timestamp)
                """, java.util.UUID.randomUUID().toString().substring(0, 30), name, "서울 테스트로");
        return jdbcTemplate.queryForObject(
                "select max(public_restaurant_id) from public_restaurant", Long.class);
    }

    private void connectRestaurant(Long targetPresetId, Long targetRestaurantId, int displayOrder) {
        jdbcTemplate.update("""
                insert into preset_restaurant (
                    preset_id, public_restaurant_id, display_order, description
                ) values (?, ?, ?, null)
                """, targetPresetId, targetRestaurantId, displayOrder);
    }

    private int countPresetRestaurants(Long targetPresetId) {
        return jdbcTemplate.queryForObject(
                "select count(*) from preset_restaurant where preset_id = ?",
                Integer.class,
                targetPresetId
        );
    }
}
