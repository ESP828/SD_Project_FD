package com.example.backend.restaurant.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class RestaurantCategoryControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void activeCategoriesAreReturnedInDisplayOrder() throws Exception {
        jdbcTemplate.update("""
                insert into restaurant_category (
                    parent_id, category_code, name, display_order, active
                ) values (null, 'TEST_JAPANESE', '일식', 30, true)
                """);
        jdbcTemplate.update("""
                insert into restaurant_category (
                    parent_id, category_code, name, display_order, active
                ) values (null, 'TEST_KOREAN', '한식', 10, true)
                """);
        jdbcTemplate.update("""
                insert into restaurant_category (
                    parent_id, category_code, name, display_order, active
                ) values (null, 'TEST_INACTIVE', '비활성', 1, false)
                """);

        mockMvc.perform(get("/api/public/restaurant-categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].categoryCode").value("TEST_KOREAN"))
                .andExpect(jsonPath("$.data[0].name").value("한식"))
                .andExpect(jsonPath("$.data[1].categoryCode").value("TEST_JAPANESE"));
    }
}
