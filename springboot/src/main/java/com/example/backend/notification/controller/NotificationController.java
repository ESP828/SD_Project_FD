package com.example.backend.notification.controller;

import com.example.backend.global.response.ApiResponse;
import com.example.backend.global.security.principal.AuthenticatedAccount;
import com.example.backend.notification.dto.response.NotificationCountResponse;
import com.example.backend.notification.dto.response.NotificationResponse;
import com.example.backend.notification.service.NotificationService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping
    public ApiResponse<List<NotificationResponse>> getNotifications(
            @AuthenticationPrincipal AuthenticatedAccount account
    ) {
        return ApiResponse.success(notificationService.findMyNotifications(account.accountId()));
    }

    @GetMapping("/unread-count")
    public ApiResponse<NotificationCountResponse> getUnreadCount(
            @AuthenticationPrincipal AuthenticatedAccount account
    ) {
        return ApiResponse.success(notificationService.countUnread(account.accountId()));
    }

    @PatchMapping("/{notificationId}/read")
    public ApiResponse<NotificationResponse> markRead(
            @AuthenticationPrincipal AuthenticatedAccount account,
            @PathVariable Long notificationId
    ) {
        return ApiResponse.success(notificationService.markRead(account.accountId(), notificationId));
    }

    @PatchMapping("/read-all")
    public ApiResponse<Void> markAllRead(
            @AuthenticationPrincipal AuthenticatedAccount account
    ) {
        notificationService.markAllRead(account.accountId());
        return ApiResponse.success("모든 알림을 읽었습니다.", null);
    }

    @DeleteMapping("/{notificationId}")
    public ApiResponse<Void> delete(
            @AuthenticationPrincipal AuthenticatedAccount account,
            @PathVariable Long notificationId
    ) {
        notificationService.delete(account.accountId(), notificationId);
        return ApiResponse.success("알림이 삭제되었습니다.", null);
    }
}
