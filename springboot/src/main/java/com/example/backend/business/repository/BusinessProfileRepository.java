package com.example.backend.business.repository;

import com.example.backend.business.domain.entity.BusinessProfile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface BusinessProfileRepository extends JpaRepository<BusinessProfile, Long> {

    Optional<BusinessProfile> findByAccountAccountId(Long accountId);

    Optional<BusinessProfile> findByBusinessNumber(String businessNumber);

    boolean existsByAccountAccountId(Long accountId);
}
