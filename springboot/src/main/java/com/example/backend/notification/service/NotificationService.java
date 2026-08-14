package com.example.backend.notification.service;

import com.example.backend.auth.domain.entity.Account;
import com.example.backend.auth.repository.AccountRepository;
import com.example.backend.global.exception.BusinessException;
import com.example.backend.global.exception.ErrorCode;
import com.example.backend.notification.domain.Notification;
import com.example.backend.notification.domain.NotificationType;
import com.example.backend.notification.dto.response.NotificationCountResponse;
import com.example.backend.notification.dto.response.NotificationResponse;
import com.example.backend.notification.repository.NotificationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class NotificationService {

    private static final int MAX_CONTENT_LENGTH = 255;

    private final NotificationRepository notificationRepository;
    private final AccountRepository accountRepository;

    public NotificationService(
            NotificationRepository notificationRepository,
            AccountRepository accountRepository
    ) {
        this.notificationRepository = notificationRepository;
        this.accountRepository = accountRepository;
    }

    @Transactional(readOnly = true)
    public List<NotificationResponse> findMyNotifications(Long accountId) {
        requireActiveAccount(accountId);
        return notificationRepository
                .findTop100ByAccountAccountIdOrderByCreatedAtDescNotificationIdDesc(accountId)
                .stream()
                .map(NotificationResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public NotificationCountResponse countUnread(Long accountId) {
        requireActiveAccount(accountId);
        return new NotificationCountResponse(
                notificationRepository.countByAccountAccountIdAndReadFalse(accountId)
        );
    }

    @Transactional
    public NotificationResponse markRead(Long accountId, Long notificationId) {
        requireActiveAccount(accountId);
        Notification notification = requireOwnedNotification(accountId, notificationId);
        notification.markRead();
        return NotificationResponse.from(notification);
    }

    @Transactional
    public void markAllRead(Long accountId) {
        requireActiveAccount(accountId);
        notificationRepository.findAllByAccountAccountIdAndReadFalse(accountId)
                .forEach(Notification::markRead);
    }

    @Transactional
    public void delete(Long accountId, Long notificationId) {
        requireActiveAccount(accountId);
        notificationRepository.delete(requireOwnedNotification(accountId, notificationId));
    }

    @Transactional
    public void createCommentNotification(Account recipient, String commenterNickname, Long postId) {
        create(
                recipient,
                NotificationType.COMMENT,
                normalizeNickname(commenterNickname) + "님이 회원님의 게시글에 댓글을 남겼습니다.",
                "POST",
                postId,
                "/pages/board/detail.html?postId=" + postId
        );
    }

    @Transactional
    public void createPostLikeMilestoneNotification(Account recipient, long likeCount, Long postId) {
        if (recipient == null || !recipient.isActive()) {
            return;
        }
        String content = trimContent("회원님의 게시글이 추천 " + likeCount + "개를 달성했습니다.");
        if (notificationRepository.existsByAccountAccountIdAndTypeAndTargetTypeAndTargetIdAndContent(
                recipient.getAccountId(),
                NotificationType.POST_LIKE_MILESTONE,
                "POST",
                postId,
                content
        )) {
            return;
        }
        create(
                recipient,
                NotificationType.POST_LIKE_MILESTONE,
                content,
                "POST",
                postId,
                "/pages/board/detail.html?postId=" + postId
        );
    }

    @Transactional
    public void createBusinessApprovedNotification(Account recipient, Long applicationId) {
        create(
                recipient,
                NotificationType.BUSINESS_APPROVED,
                "사업자 권한 신청이 승인되었습니다. 새 권한은 다시 로그인하면 반영됩니다.",
                "BUSINESS_APPLICATION",
                applicationId,
                "/pages/business/index.html"
        );
    }

    @Transactional
    public void createBusinessRejectedNotification(Account recipient, Long applicationId) {
        create(
                recipient,
                NotificationType.BUSINESS_REJECTED,
                "사업자 권한 신청이 반려되었습니다. 사업자 페이지에서 사유를 확인해 주세요.",
                "BUSINESS_APPLICATION",
                applicationId,
                "/pages/business/index.html"
        );
    }

    private void create(
            Account recipient,
            NotificationType type,
            String content,
            String targetType,
            Long targetId,
            String targetUrl
    ) {
        if (recipient == null || !recipient.isActive()) {
            return;
        }
        notificationRepository.save(new Notification(
                recipient,
                type,
                trimContent(content),
                targetType,
                targetId,
                requireInternalTargetUrl(targetUrl)
        ));
    }

    private Notification requireOwnedNotification(Long accountId, Long notificationId) {
        return notificationRepository
                .findByNotificationIdAndAccountAccountId(notificationId, accountId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
    }

    private Account requireActiveAccount(Long accountId) {
        return accountRepository.findById(accountId)
                .filter(Account::isActive)
                .orElseThrow(() -> new BusinessException(ErrorCode.ACCOUNT_NOT_FOUND));
    }

    private String trimContent(String content) {
        String normalized = content == null ? "" : content.strip();
        if (normalized.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        return normalized.length() <= MAX_CONTENT_LENGTH
                ? normalized
                : normalized.substring(0, MAX_CONTENT_LENGTH);
    }

    private String requireInternalTargetUrl(String targetUrl) {
        String normalized = targetUrl == null ? "" : targetUrl.strip();
        if (!normalized.startsWith("/pages/")
                || normalized.startsWith("//")
                || normalized.indexOf('\\') >= 0
                || normalized.indexOf('\r') >= 0
                || normalized.indexOf('\n') >= 0) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        return normalized;
    }

    private String normalizeNickname(String nickname) {
        String normalized = nickname == null ? "사용자" : nickname.strip();
        return normalized.isEmpty() ? "사용자" : normalized;
    }
}
