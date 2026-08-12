package com.example.backend.global.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FooduckDatabaseNameGuardTest {

    @Test
    @DisplayName("MySQL의 foodduck 스키마만 허용한다")
    void acceptsCanonicalFoodduckDatabase() {
        assertDoesNotThrow(() -> FooduckDatabaseNameGuard.validate(
                "jdbc:mysql://localhost:3306/foodduck"
                        + "?serverTimezone=Asia/Seoul&characterEncoding=UTF-8"
        ));
    }

    @Test
    @DisplayName("이전 fooduck 스키마와 빈 MySQL 스키마를 거부한다")
    void rejectsWrongOrMissingMysqlDatabase() {
        assertThrows(IllegalStateException.class, () ->
                FooduckDatabaseNameGuard.validate(
                        "jdbc:mysql://localhost:3306/fooduck"
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
