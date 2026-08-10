package com.example.backend.admin.service;

import com.example.backend.admin.dto.response.AdminOverviewResponse;
import com.example.backend.admin.query.AdminOverviewQueryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminOverviewService {

    private final AdminOverviewQueryRepository queryRepository;

    public AdminOverviewService(AdminOverviewQueryRepository queryRepository) {
        this.queryRepository = queryRepository;
    }

    @Transactional(readOnly = true)
    public AdminOverviewResponse getOverview() {
        return queryRepository.findOverview();
    }
}
