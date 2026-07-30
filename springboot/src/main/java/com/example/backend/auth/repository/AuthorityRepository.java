package com.example.backend.auth.repository;

import com.example.backend.auth.domain.entity.Authority;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AuthorityRepository extends JpaRepository<Authority, Short> {

    Optional<Authority> findByAuthorityCode(String authorityCode);
}
