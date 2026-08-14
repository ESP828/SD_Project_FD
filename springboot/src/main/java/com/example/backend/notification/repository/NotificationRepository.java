package com.example.backend.notification.repository;

import com.example.backend.notification.domain.Notification;
import com.example.backend.notification.domain.NotificationType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    List<Notification> findTop100ByAccountAccountIdOrderByCreatedAtDescNotificationIdDesc(Long accountId);

    List<Notification> findAllByAccountAccountIdAndReadFalse(Long accountId);

    Optional<Notification> findByNotificationIdAndAccountAccountId(Long notificationId, Long accountId);

    long countByAccountAccountIdAndReadFalse(Long accountId);

    boolean existsByAccountAccountIdAndType(Long accountId, NotificationType type);

    boolean existsByAccountAccountIdAndTypeAndTargetTypeAndTargetIdAndContent(
            Long accountId,
            NotificationType type,
            String targetType,
            Long targetId,
            String content
    );
}
