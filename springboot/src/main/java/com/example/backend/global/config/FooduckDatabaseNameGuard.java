package com.example.backend.global.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 팀 공용 MySQL 스키마 이름을 fooduck으로 고정한다.
 *
 * <p>브랜드 표기 Fooduck과 달리 실제 DB 식별자는 반드시 {@code fooduck}이다.
 * 환경변수 DB_URL에 과거 오타인 foodduck이나 빈 스키마가 들어오면 애플리케이션
 * 시작 단계에서 중단해 다른 데이터베이스에 쓰는 사고를 막는다.</p>
 */
@Component
public class FooduckDatabaseNameGuard {

    private static final String CANONICAL_DATABASE_NAME = "fooduck";
    private static final Pattern MYSQL_URL_PATTERN = Pattern.compile(
            "^jdbc:mysql:(?://|loadbalance://|replication://)[^/]+/([^?;]+)(?:[?;].*)?$",
            Pattern.CASE_INSENSITIVE
    );

    public FooduckDatabaseNameGuard(
            @Value("${spring.datasource.url}") String datasourceUrl
    ) {
        validate(datasourceUrl);
    }

    static void validate(String datasourceUrl) {
        if (datasourceUrl == null
                || !datasourceUrl.toLowerCase(Locale.ROOT).startsWith("jdbc:mysql:")) {
            return;
        }

        Matcher matcher = MYSQL_URL_PATTERN.matcher(datasourceUrl.strip());
        if (!matcher.matches()
                || !CANONICAL_DATABASE_NAME.equalsIgnoreCase(matcher.group(1))) {
            throw new IllegalStateException(
                    "MySQL DB_URL의 스키마 이름은 반드시 'fooduck'이어야 합니다."
            );
        }
    }
}
