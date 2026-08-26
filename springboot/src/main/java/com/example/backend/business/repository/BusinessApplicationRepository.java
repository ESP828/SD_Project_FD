package com.example.backend.business.repository;

import com.example.backend.auth.domain.entity.Account;
import com.example.backend.business.domain.entity.BusinessApplication;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BusinessApplicationRepository extends JpaRepository<BusinessApplication, Long> {

    List<BusinessApplication> findAllByAccountOrderByCreatedAtDesc(Account account);

    boolean existsByAccountAndStatus(Account account, BusinessApplication.Status status);

    Page<BusinessApplication> findAllByOrderByCreatedAtDesc(Pageable pageable);

    Page<BusinessApplication> findAllByStatusOrderByCreatedAtDesc(BusinessApplication.Status status, Pageable pageable);
}
