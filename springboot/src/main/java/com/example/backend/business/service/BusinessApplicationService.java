package com.example.backend.business.service;

import com.example.backend.auth.domain.entity.Account;
import com.example.backend.auth.domain.type.AuthorityCode;
import com.example.backend.auth.repository.AccountRepository;
import com.example.backend.auth.service.AuthorityService;
import com.example.backend.business.domain.entity.BusinessApplication;
import com.example.backend.business.domain.entity.BusinessProfile;
import com.example.backend.business.dto.request.BusinessApplicationRequest;
import com.example.backend.business.dto.response.BusinessApplicationResponse;
import com.example.backend.business.integration.nts.NtsBusinessVerificationClient;
import com.example.backend.business.policy.BusinessRegistrationNumberValidator;
import com.example.backend.business.repository.BusinessApplicationRepository;
import com.example.backend.business.repository.BusinessProfileRepository;
import com.example.backend.global.exception.BusinessException;
import com.example.backend.global.exception.ErrorCode;
import com.example.backend.notification.service.NotificationService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class BusinessApplicationService {

    private static final DateTimeFormatter NTS_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");

    // 테스트 편의용 예외 트리거. 사업자등록번호에 "0"만 입력하면 체크섬·국세청 진위확인을
    // 전부 건너뛴다. 저장은 "0" 그대로 하지 않고 매번 새 값을 만들어서, 여러 명이 동시에
    // "0"으로 테스트해도 business_number 유니크 제약(대기중 신청·승인된 사업자)에 걸리지 않게 한다.
    private static final String VERIFICATION_BYPASS_INPUT = "0";

    private final BusinessApplicationRepository businessApplicationRepository;
    private final AccountRepository accountRepository;
    private final AuthorityService authorityService;
    private final NtsBusinessVerificationClient ntsBusinessVerificationClient;
    private final BusinessProfileRepository businessProfileRepository;
    private final NotificationService notificationService;

    public BusinessApplicationService(
            BusinessApplicationRepository businessApplicationRepository,
            AccountRepository accountRepository,
            AuthorityService authorityService,
            NtsBusinessVerificationClient ntsBusinessVerificationClient,
            BusinessProfileRepository businessProfileRepository,
            NotificationService notificationService
    ) {
        this.businessApplicationRepository = businessApplicationRepository;
        this.accountRepository = accountRepository;
        this.authorityService = authorityService;
        this.ntsBusinessVerificationClient = ntsBusinessVerificationClient;
        this.businessProfileRepository = businessProfileRepository;
        this.notificationService = notificationService;
    }

    @Transactional
    public BusinessApplicationResponse submitApplication(Long accountId, BusinessApplicationRequest request) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ACCOUNT_NOT_FOUND));

        if (businessApplicationRepository.existsByAccountAndStatus(account, BusinessApplication.Status.PENDING)) {
            throw new BusinessException(ErrorCode.DATA_CONFLICT);
        }

        String normalizedNumber;
        if (VERIFICATION_BYPASS_INPUT.equals(request.businessNumber() == null ? null : request.businessNumber().trim())) {
            // 체크섬·국세청 검증을 건너뛴 신청이라는 걸 값 자체로 알아볼 수 있게 TEST- 접두사를 쓴다.
            normalizedNumber = "TEST-" + System.currentTimeMillis() + ThreadLocalRandom.current().nextInt(10, 99);
        } else {
            normalizedNumber = BusinessRegistrationNumberValidator.normalize(request.businessNumber());
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
        createOrUpdateBusinessProfile(application);
        notificationService.createBusinessApprovedNotification(
                application.getAccount(),
                application.getApplicationId()
        );
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
        notificationService.createBusinessRejectedNotification(
                application.getAccount(),
                application.getApplicationId()
        );
        return BusinessApplicationResponse.from(application);
    }

    private void createOrUpdateBusinessProfile(BusinessApplication application) {
        Long accountId = application.getAccount().getAccountId();
        businessProfileRepository.findByBusinessNumber(application.getBusinessNumber())
                .filter(profile -> !profile.getAccount().getAccountId().equals(accountId))
                .ifPresent(profile -> {
                    throw new BusinessException(ErrorCode.DATA_CONFLICT);
                });

        BusinessProfile profile = businessProfileRepository.findByAccountAccountId(accountId)
                .orElseGet(() -> new BusinessProfile(application));
        profile.updateFrom(application);
        businessProfileRepository.save(profile);
    }
}
