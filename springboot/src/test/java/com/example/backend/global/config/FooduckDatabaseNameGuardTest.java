package com.example.backend.global.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FooduckDatabaseNameGuardTest {

    @Test
    @DisplayName("MySQL의 fooduck 스키마만 허용한다")
    void acceptsCanonicalFooduckDatabase() {
        assertDoesNotThrow(() -> FooduckDatabaseNameGuard.validate(
                "jdbc:mysql://localhost:3306/fooduck"
                        + "?serverTimezone=Asia/Seoul&characterEncoding=UTF-8"
        ));
    }

    @Test
    @DisplayName("과거 오타 foodduck과 빈 MySQL 스키마를 거부한다")
    void rejectsWrongOrMissingMysqlDatabase() {
        assertThrows(IllegalStateException.class, () ->
                FooduckDatabaseNameGuard.validate(
                        "jdbc:mysql://localhost:3306/foodduck"
                )
        );
        assertThrows(IllegalStateException.class, () ->
                FooduckDatabaseNameGuard.validate(
                        "jdbc:mysql://localhost:3306/"
                )
        );
    }

    @Test
    @DisplayName("H2 테스트 데이터베이스에는 MySQL 이름 검사를 적용하지 않는다")
    void ignoresNonMysqlTestDatabase() {
        assertDoesNotThrow(() -> FooduckDatabaseNameGuard.validate(
                "jdbc:h2:mem:foodduck_test;MODE=MySQL"
        ));
    }
}
