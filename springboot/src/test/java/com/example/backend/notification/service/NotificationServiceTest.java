package com.example.backend.notification.service;

import com.example.backend.auth.domain.entity.Account;
import com.example.backend.auth.repository.AccountRepository;
import com.example.backend.global.exception.BusinessException;
import com.example.backend.notification.domain.Notification;
import com.example.backend.notification.domain.NotificationType;
import com.example.backend.notification.repository.NotificationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private NotificationRepository notificationRepository;
    @Mock
    private AccountRepository accountRepository;

    private NotificationService notificationService;

    @BeforeEach
    void setUp() {
        notificationService = new NotificationService(notificationRepository, accountRepository);
    }

    @Test
    void commentNotificationUsesInternalPostTarget() {
        Account recipient = account(1L);

        notificationService.createCommentNotification(recipient, "댓글작성자", 10L);

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(captor.capture());
        Notification saved = captor.getValue();
        assertThat(saved.getType()).isEqualTo(NotificationType.COMMENT);
        assertThat(saved.getTargetUrl()).isEqualTo("/pages/board/detail.html?postId=10");
        assertThat(saved.getContent()).contains("댓글작성자");
    }

    @Test
    void samePostLikeMilestoneIsNotCreatedTwice() {
        Account recipient = account(1L);
        String content = "회원님의 게시글이 추천 10개를 달성했습니다.";
        when(notificationRepository.existsByAccountAccountIdAndTypeAndTargetTypeAndTargetIdAndContent(
                1L,
                NotificationType.POST_LIKE_MILESTONE,
                "POST",
                10L,
                content
        )).thenReturn(true);

        notificationService.createPostLikeMilestoneNotification(recipient, 10, 10L);

        verify(notificationRepository, never()).save(any(Notification.class));
    }

    @Test
    void markReadRequiresNotificationOwnership() {
        Account owner = account(1L);
        Notification notification = notification(owner, 100L);
        when(accountRepository.findById(1L)).thenReturn(Optional.of(owner));
        when(notificationRepository.findByNotificationIdAndAccountAccountId(100L, 1L))
                .thenReturn(Optional.of(notification));

        notificationService.markRead(1L, 100L);

        assertThat(notification.isRead()).isTrue();
        assertThat(notification.getReadAt()).isNotNull();
    }

    @Test
    void anotherAccountsNotificationIsNotExposed() {
        Account requester = account(2L);
        when(accountRepository.findById(2L)).thenReturn(Optional.of(requester));
        when(notificationRepository.findByNotificationIdAndAccountAccountId(100L, 2L))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> notificationService.markRead(2L, 100L))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void markAllReadChangesOnlyLoadedUnreadNotifications() {
        Account owner = account(1L);
        Notification first = notification(owner, 100L);
        Notification second = notification(owner, 101L);
        when(accountRepository.findById(1L)).thenReturn(Optional.of(owner));
        when(notificationRepository.findAllByAccountAccountIdAndReadFalse(1L))
                .thenReturn(List.of(first, second));

        notificationService.markAllRead(1L);

        assertThat(first.isRead()).isTrue();
        assertThat(second.isRead()).isTrue();
    }

    private Account account(Long accountId) {
        Account account = Account.local(
                "user" + accountId,
                "user" + accountId + "@example.com",
                "사용자" + accountId
        );
        ReflectionTestUtils.setField(account, "accountId", accountId);
        return account;
    }

    private Notification notification(Account account, Long notificationId) {
        Notification notification = new Notification(
                account,
                NotificationType.COMMENT,
                "새 댓글",
                "POST",
                10L,
                "/pages/board/detail.html?postId=10"
        );
        ReflectionTestUtils.setField(notification, "notificationId", notificationId);
        ReflectionTestUtils.setField(notification, "createdAt", LocalDateTime.now());
        return notification;
    }
}
