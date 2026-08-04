package com.example.backend.auth.repository;

import com.example.backend.auth.domain.entity.Account;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface AccountRepository extends JpaRepository<Account, Long> {

    Optional<Account> findByLoginId(String loginId);

    Optional<Account> findByEmail(String email);

    Optional<Account> findByLoginIdAndEmail(String loginId, String email);

    boolean existsByLoginId(String loginId);

    boolean existsByEmail(String email);

    boolean existsByNickname(String nickname);

    /**
     * 관리자 계정 목록: 아이디·닉네임·이메일 검색과 최고 권한(account_authority의 MAX authority_id) 필터를 지원한다.
     * keyword가 빈 문자열이면 검색 조건을 생략하고, roleId가 -1이면 권한 필터를 생략한다.
     */
    @Query(value = """
            SELECT a.account_id AS accountId, a.login_id AS loginId, a.nickname AS nickname,
                   a.email AS email, a.status AS status, a.created_at AS createdAt, a.last_login_at AS lastLoginAt,
                   COALESCE(MAX(aa.authority_id), 0) AS highestAuthorityId
            FROM account a
            LEFT JOIN account_authority aa ON aa.account_id = a.account_id
            WHERE a.deleted_at IS NULL
              AND (:keyword = '' OR a.login_id LIKE CONCAT('%', :keyword, '%')
                   OR a.nickname LIKE CONCAT('%', :keyword, '%')
                   OR a.email LIKE CONCAT('%', :keyword, '%'))
            GROUP BY a.account_id, a.login_id, a.nickname, a.email, a.status, a.created_at, a.last_login_at
            HAVING (:roleId = -1 OR COALESCE(MAX(aa.authority_id), 0) = :roleId)
            ORDER BY a.created_at DESC
            LIMIT :limit
            """, nativeQuery = true)
    List<AdminAccountRow> searchAccounts(
            @Param("keyword") String keyword,
            @Param("roleId") int roleId,
            @Param("limit") int limit
    );

    interface AdminAccountRow {
        Long getAccountId();

        String getLoginId();

        String getNickname();

        String getEmail();

        String getStatus();

        LocalDateTime getCreatedAt();

        LocalDateTime getLastLoginAt();

        Integer getHighestAuthorityId();
    }
}
