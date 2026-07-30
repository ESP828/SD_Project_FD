package com.example.backend.mypage.query;

import com.example.backend.mypage.dto.response.MyPageActivityResponse.CommentItem;
import com.example.backend.mypage.dto.response.MyPageActivityResponse.FavoriteItem;
import com.example.backend.mypage.dto.response.MyPageActivityResponse.NotificationItem;
import com.example.backend.mypage.dto.response.MyPageActivityResponse.PostItem;
import com.example.backend.mypage.dto.response.MyPageActivityResponse.ReviewItem;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public class MyPageActivityQueryRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public MyPageActivityQueryRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public ActivityCounts findCounts(Long accountId) {
        var parameters = parameters(accountId);
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

    public List<FavoriteItem> findFavorites(Long accountId) {
        return jdbcTemplate.query("""
                        select r.restaurant_id,
                               r.name as restaurant_name,
                               rc.name as category_name,
                               r.address,
                               r.description,
                               f.created_at
                          from favorite f
                          join restaurant r
                            on r.restaurant_id = f.restaurant_id
                          left join restaurant_category rc
                            on rc.category_id = r.category_id
                         where f.account_id = :accountId
                           and r.status = 'ACTIVE'
                         order by f.created_at desc
                         limit 100
                        """,
                parameters(accountId),
                (resultSet, rowNumber) -> new FavoriteItem(
                        resultSet.getLong("restaurant_id"),
                        resultSet.getString("restaurant_name"),
                        resultSet.getString("category_name"),
                        resultSet.getString("address"),
                        resultSet.getString("description"),
                        resultSet.getObject("created_at", LocalDateTime.class)
                ));
    }

    public List<ReviewItem> findReviews(Long accountId) {
        return jdbcTemplate.query("""
                        select rv.review_id,
                               rv.restaurant_id,
                               r.name as restaurant_name,
                               rv.rating,
                               rv.content,
                               rv.created_at,
                               rv.updated_at
                          from review rv
                          join restaurant r
                            on r.restaurant_id = rv.restaurant_id
                         where rv.account_id = :accountId
                           and rv.status = 'ACTIVE'
                         order by rv.created_at desc
                         limit 100
                        """,
                parameters(accountId),
                (resultSet, rowNumber) -> new ReviewItem(
                        resultSet.getLong("review_id"),
                        resultSet.getLong("restaurant_id"),
                        resultSet.getString("restaurant_name"),
                        resultSet.getInt("rating"),
                        resultSet.getString("content"),
                        resultSet.getObject("created_at", LocalDateTime.class),
                        resultSet.getObject("updated_at", LocalDateTime.class)
                ));
    }

    public List<PostItem> findPosts(Long accountId) {
        return jdbcTemplate.query("""
                        select p.post_id,
                               p.board_type,
                               p.category,
                               p.title,
                               p.view_count,
                               p.like_count,
                               (
                                   select count(*)
                                     from post_comment pc
                                    where pc.post_id = p.post_id
                                      and pc.status = 'ACTIVE'
                               ) as comment_count,
                               p.created_at,
                               p.updated_at
                          from post p
                         where p.account_id = :accountId
                           and p.status = 'ACTIVE'
                         order by p.created_at desc
                         limit 100
                        """,
                parameters(accountId),
                (resultSet, rowNumber) -> new PostItem(
                        resultSet.getLong("post_id"),
                        resultSet.getString("board_type"),
                        resultSet.getString("category"),
                        resultSet.getString("title"),
                        resultSet.getLong("view_count"),
                        resultSet.getLong("like_count"),
                        resultSet.getLong("comment_count"),
                        resultSet.getObject("created_at", LocalDateTime.class),
                        resultSet.getObject("updated_at", LocalDateTime.class)
                ));
    }

    public List<CommentItem> findComments(Long accountId) {
        return jdbcTemplate.query("""
                        select pc.comment_id,
                               pc.post_id,
                               p.title as post_title,
                               pc.content,
                               pc.created_at,
                               pc.updated_at
                          from post_comment pc
                          join post p
                            on p.post_id = pc.post_id
                         where pc.account_id = :accountId
                           and pc.status = 'ACTIVE'
                           and p.status = 'ACTIVE'
                         order by pc.created_at desc
                         limit 100
                        """,
                parameters(accountId),
                (resultSet, rowNumber) -> new CommentItem(
                        resultSet.getLong("comment_id"),
                        resultSet.getLong("post_id"),
                        resultSet.getString("post_title"),
                        resultSet.getString("content"),
                        resultSet.getObject("created_at", LocalDateTime.class),
                        resultSet.getObject("updated_at", LocalDateTime.class)
                ));
    }

    public List<NotificationItem> findUnreadNotifications(Long accountId) {
        return jdbcTemplate.query("""
                        select notification_id,
                               type,
                               content,
                               target_type,
                               target_id,
                               target_url,
                               is_read,
                               created_at
                          from notification
                         where account_id = :accountId
                           and is_read = 0
                         order by created_at desc
                         limit 100
                        """,
                parameters(accountId),
                (resultSet, rowNumber) -> {
                    long targetIdValue = resultSet.getLong("target_id");
                    Long targetId = resultSet.wasNull() ? null : targetIdValue;
                    return new NotificationItem(
                            resultSet.getLong("notification_id"),
                            resultSet.getString("type"),
                            resultSet.getString("content"),
                            resultSet.getString("target_type"),
                            targetId,
                            resultSet.getString("target_url"),
                            resultSet.getBoolean("is_read"),
                            resultSet.getObject("created_at", LocalDateTime.class)
                    );
                });
    }

    private MapSqlParameterSource parameters(Long accountId) {
        return new MapSqlParameterSource("accountId", accountId);
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
