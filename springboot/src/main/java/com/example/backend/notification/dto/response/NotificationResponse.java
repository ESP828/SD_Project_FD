package com.example.backend.notification.dto.response;

import com.example.backend.notification.domain.Notification;

import java.time.LocalDateTime;

public record NotificationResponse(
        Long notificationId,
        String type,
        String content,
        String targetType,
        Long targetId,
        String targetUrl,
        boolean read,
        LocalDateTime readAt,
        LocalDateTime createdAt
) {
    public static NotificationResponse from(Notification notification) {
        return new NotificationResponse(
                notification.getNotificationId(),
                notification.getType().name(),
                notification.getContent(),
                notification.getTargetType(),
                notification.getTargetId(),
                notification.getTargetUrl(),
                notification.isRead(),
                notification.getReadAt(),
                notification.getCreatedAt()
        );
    }
}
