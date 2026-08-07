package com.example.backend.global.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 팀 공용 MySQL 스키마 이름을 foodduck으로 고정한다.
 *
 * <p>기존에는 실제 DB 식별자를 {@code fooduck}으로 고정해 과거 오타인 {@code foodduck}을
 * 막았지만, 팀에서 운영 스키마를 {@code foodduck}으로 새로 구성하면서 기준이 바뀌었다.
 * 이제는 예전에 쓰던 {@code fooduck}(구 스키마)이 DB_URL에 들어오면 애플리케이션 시작
 * 단계에서 막아, 더 이상 쓰지 않는 옛 데이터베이스에 실수로 연결/기록하는 사고를 방지한다.</p>
 */
@Component
public class FooduckDatabaseNameGuard {

    private static final String CANONICAL_DATABASE_NAME = "foodduck";
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
                    "MySQL DB_URL의 스키마 이름은 반드시 'foodduck'이어야 합니다."
            );
        }
    }
}
