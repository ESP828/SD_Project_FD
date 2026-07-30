package com.example.backend.mypage.query;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class MyPageActivityQueryRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public MyPageActivityQueryRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public ActivityCounts findCounts(Long accountId) {
        var parameters = new MapSqlParameterSource("accountId", accountId);
        return new ActivityCounts(
                count("select count(*) from favorite where account_id = :accountId", parameters),
                count("""
                        select count(*)
                          from review
                         where account_id = :accountId
                           and status = 'ACTIVE'
                        """, parameters),
                count("""
                        select count(*)
                          from post
                         where account_id = :accountId
                           and status = 'ACTIVE'
                        """, parameters),
                count("""
                        select count(*)
                          from post_comment
                         where account_id = :accountId
                           and status = 'ACTIVE'
                        """, parameters),
                count("""
                        select count(*)
                          from notification
                         where account_id = :accountId
                           and is_read = 0
                        """, parameters)
        );
    }

    private long count(String sql, MapSqlParameterSource parameters) {
        Long value = jdbcTemplate.queryForObject(sql, parameters, Long.class);
        return value == null ? 0 : value;
    }

    public record ActivityCounts(
            long favorites,
            long reviews,
            long posts,
            long comments,
            long unreadNotifications
    ) {
    }
}
