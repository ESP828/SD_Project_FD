package com.example.backend.auth.repository;

import com.example.backend.auth.domain.entity.AccountCredential;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccountCredentialRepository extends JpaRepository<AccountCredential, Long> {
}
