package com.example.backend.auth.repository;

import com.example.backend.auth.domain.entity.AccountAuthority;
import com.example.backend.auth.domain.entity.AccountAuthorityId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AccountAuthorityRepository extends JpaRepository<AccountAuthority, AccountAuthorityId> {

    List<AccountAuthority> findAllByIdAccountId(Long accountId);
}
