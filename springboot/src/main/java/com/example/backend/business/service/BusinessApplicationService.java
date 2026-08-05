package com.example.backend.business.service;

import com.example.backend.auth.domain.entity.Account;
import com.example.backend.auth.domain.type.AuthorityCode;
import com.example.backend.auth.repository.AccountRepository;
import com.example.backend.auth.service.AuthorityService;
import com.example.backend.business.domain.entity.BusinessApplication;
import com.example.backend.business.dto.request.BusinessApplicationRequest;
import com.example.backend.business.dto.response.BusinessApplicationResponse;
import com.example.backend.business.integration.nts.NtsBusinessVerificationClient;
import com.example.backend.business.policy.BusinessRegistrationNumberValidator;
import com.example.backend.business.repository.BusinessApplicationRepository;
import com.example.backend.global.exception.BusinessException;
import com.example.backend.global.exception.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
public class BusinessApplicationService {

    private static final DateTimeFormatter NTS_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final BusinessApplicationRepository businessApplicationRepository;
    private final AccountRepository accountRepository;
    private final AuthorityService authorityService;
    private final NtsBusinessVerificationClient ntsBusinessVerificationClient;

    public BusinessApplicationService(
            BusinessApplicationRepository businessApplicationRepository,
            AccountRepository accountRepository,
            AuthorityService authorityService,
            NtsBusinessVerificationClient ntsBusinessVerificationClient
    ) {
        this.businessApplicationRepository = businessApplicationRepository;
        this.accountRepository = accountRepository;
        this.authorityService = authorityService;
        this.ntsBusinessVerificationClient = ntsBusinessVerificationClient;
    }

    @Transactional
    public BusinessApplicationResponse submitApplication(Long accountId, BusinessApplicationRequest request) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ACCOUNT_NOT_FOUND));

        if (businessApplicationRepository.existsByAccountAndStatus(account, BusinessApplication.Status.PENDING)) {
            throw new BusinessException(ErrorCode.DATA_CONFLICT);
        }

        String normalizedNumber = BusinessRegistrationNumberValidator.normalize(request.businessNumber());
        if (!BusinessRegistrationNumberValidator.isValidChecksum(normalizedNumber)) {
            throw new BusinessException(ErrorCode.INVALID_BUSINESS_NUMBER);
        }

        if (ntsBusinessVerificationClient.isConfigured()) {
            boolean matched = ntsBusinessVerificationClient.verify(
                    normalizedNumber,
                    request.openedAt().format(NTS_DATE_FORMAT),
                    request.representativeName()
            );
            if (!matched) {
                throw new BusinessException(ErrorCode.BUSINESS_REGISTRATION_MISMATCH);
            }
        }

        BusinessApplication application = new BusinessApplication(
                account,
                request.businessName(),
                normalizedNumber,
                request.representativeName(),
                request.openedAt(),
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
