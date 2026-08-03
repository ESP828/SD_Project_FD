package com.example.backend.auth.repository;

import com.example.backend.auth.domain.entity.Account;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AccountRepository extends JpaRepository<Account, Long> {

    Optional<Account> findByLoginId(String loginId);

    Optional<Account> findByEmail(String email);

    Optional<Account> findByLoginIdAndEmail(String loginId, String email);

    boolean existsByLoginId(String loginId);

    boolean existsByEmail(String email);

    boolean existsByNickname(String nickname);
}
