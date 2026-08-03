package com.example.backend.business.service;

import com.example.backend.auth.domain.entity.Account;
import com.example.backend.auth.domain.type.AuthorityCode;
import com.example.backend.auth.repository.AccountRepository;
import com.example.backend.auth.service.AuthorityService;
import com.example.backend.business.domain.entity.BusinessApplication;
import com.example.backend.business.dto.request.BusinessApplicationRequest;
import com.example.backend.business.dto.response.BusinessApplicationResponse;
import com.example.backend.business.repository.BusinessApplicationRepository;
import com.example.backend.global.exception.BusinessException;
import com.example.backend.global.exception.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class BusinessApplicationService {

    private final BusinessApplicationRepository businessApplicationRepository;
    private final AccountRepository accountRepository;
    private final AuthorityService authorityService;

    public BusinessApplicationService(
            BusinessApplicationRepository businessApplicationRepository,
            AccountRepository accountRepository,
            AuthorityService authorityService
    ) {
        this.businessApplicationRepository = businessApplicationRepository;
        this.accountRepository = accountRepository;
        this.authorityService = authorityService;
    }

    @Transactional
    public BusinessApplicationResponse submitApplication(Long accountId, BusinessApplicationRequest request) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ACCOUNT_NOT_FOUND));

        if (businessApplicationRepository.existsByAccountAndStatus(account, BusinessApplication.Status.PENDING)) {
            throw new BusinessException(ErrorCode.DATA_CONFLICT);
        }

        BusinessApplication application = new BusinessApplication(
                account,
                request.businessName(),
                request.businessNumber(),
                request.representativeName(),
                request.contact(),
                request.reason()
        );
        BusinessApplication saved = businessApplicationRepository.save(application);
        return BusinessApplicationResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public List<BusinessApplicationResponse> findMyApplications(Long accountId) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ACCOUNT_NOT_FOUND));
        return businessApplicationRepository.findAllByAccountOrderByCreatedAtDesc(account)
                .stream()
                .map(BusinessApplicationResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<BusinessApplicationResponse> findAllApplications() {
        return businessApplicationRepository.findAllByOrderByCreatedAtDesc()
                .stream()
                .map(BusinessApplicationResponse::from)
                .toList();
    }

    @Transactional
    public BusinessApplicationResponse approve(Long adminAccountId, Long applicationId) {
        Account admin = accountRepository.findById(adminAccountId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ACCOUNT_NOT_FOUND));
        BusinessApplication application = businessApplicationRepository.findById(applicationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        try {
            application.approve(admin);
        } catch (IllegalStateException exception) {
            throw new BusinessException(ErrorCode.DATA_CONFLICT);
        }
        authorityService.grant(application.getAccount().getAccountId(), AuthorityCode.ROLE_BUSINESS);
        return BusinessApplicationResponse.from(application);
    }

    @Transactional
    public BusinessApplicationResponse reject(Long adminAccountId, Long applicationId, String rejectReason) {
        Account admin = accountRepository.findById(adminAccountId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ACCOUNT_NOT_FOUND));
        BusinessApplication application = businessApplicationRepository.findById(applicationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        try {
            application.reject(admin, rejectReason);
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        } catch (IllegalStateException exception) {
            throw new BusinessException(ErrorCode.DATA_CONFLICT);
        }
        return BusinessApplicationResponse.from(application);
    }
}
