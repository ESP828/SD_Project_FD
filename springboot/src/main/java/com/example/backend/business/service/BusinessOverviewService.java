package com.example.backend.business.service;

import com.example.backend.business.dto.response.BusinessOverviewResponse;
import com.example.backend.business.query.BusinessOverviewQueryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BusinessOverviewService {

    private final BusinessOverviewQueryRepository queryRepository;

    public BusinessOverviewService(BusinessOverviewQueryRepository queryRepository) {
        this.queryRepository = queryRepository;
    }

    @Transactional(readOnly = true)
    public BusinessOverviewResponse getOverview(Long accountId) {
        return queryRepository.findOverview(accountId);
    }
}
