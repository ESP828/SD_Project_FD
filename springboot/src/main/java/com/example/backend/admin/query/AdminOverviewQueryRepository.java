package com.example.backend.admin.query;

import com.example.backend.admin.dto.response.AdminOverviewResponse;
import com.example.backend.admin.dto.response.AdminOverviewResponse.PendingBusinessApplication;
import org.springframework.jdbc.core.namedparam.EmptySqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public class AdminOverviewQueryRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public AdminOverviewQueryRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public AdminOverviewResponse findOverview() {
        EmptySqlParameterSource parameters = EmptySqlParameterSource.INSTANCE;
        Counts counts = jdbcTemplate.queryForObject("""
                select (select count(*)
                          from account a
                         where a.deleted_at is null) as account_count,
                       (select count(*)
                          from business_application ba
                         where ba.status = 'PENDING') as pending_business_application_count,
                       (select count(*)
                          from restaurant r
                         where r.status = 'ACTIVE') as active_restaurant_count,
                       (select count(*)
                          from post p
                         where p.status = 'ACTIVE') as community_post_count,
                       (select count(*)
                          from preset p
                         where p.status = 'ACTIVE') as active_preset_count
                """, parameters, (resultSet, rowNumber) -> new Counts(
                resultSet.getLong("account_count"),
                resultSet.getLong("pending_business_application_count"),
                resultSet.getLong("active_restaurant_count"),
                resultSet.getLong("community_post_count"),
                resultSet.getLong("active_preset_count")
        ));

        List<PendingBusinessApplication> pendingApplications = jdbcTemplate.query("""
                select ba.application_id,
                       a.login_id as applicant_login_id,
                       a.nickname as applicant_nickname,
                       ba.business_name,
                       ba.representative_name,
                       ba.created_at
                  from business_application ba
                  join account a on a.account_id = ba.account_id
                 where ba.status = 'PENDING'
                 order by ba.created_at desc, ba.application_id desc
                 limit 5
                """, parameters, (resultSet, rowNumber) -> new PendingBusinessApplication(
                resultSet.getLong("application_id"),
                resultSet.getString("applicant_login_id"),
                resultSet.getString("applicant_nickname"),
                resultSet.getString("business_name"),
                resultSet.getString("representative_name"),
                resultSet.getObject("created_at", LocalDateTime.class)
        ));

        Counts safeCounts = counts == null ? Counts.empty() : counts;
        return new AdminOverviewResponse(
                safeCounts.accountCount(),
                safeCounts.pendingBusinessApplicationCount(),
                safeCounts.activeRestaurantCount(),
                safeCounts.communityPostCount(),
                safeCounts.activePresetCount(),
                pendingApplications
        );
    }

    private record Counts(
            long accountCount,
            long pendingBusinessApplicationCount,
            long activeRestaurantCount,
            long communityPostCount,
            long activePresetCount
    ) {
        private static Counts empty() {
            return new Counts(0, 0, 0, 0, 0);
        }
    }
}
