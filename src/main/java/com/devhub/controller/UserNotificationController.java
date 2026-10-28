package com.devhub.controller;

import com.devhub.notification.UserNotification;
import com.devhub.service.UserNotificationService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/notifications")
public class UserNotificationController {
    private final UserNotificationService notifications;

    public UserNotificationController(UserNotificationService notifications) {
        this.notifications = notifications;
    }

    @GetMapping
    public List<UserNotification> list(@AuthenticationPrincipal Jwt jwt) {
        return notifications.list(jwt.getSubject());
    }

    @GetMapping("/unread-count")
    public Map<String, Long> unreadCount(@AuthenticationPrincipal Jwt jwt) {
        return Map.of("unreadCount", notifications.unreadCount(jwt.getSubject()));
    }

    @PatchMapping("/{notificationId}/read")
    public UserNotification markRead(@AuthenticationPrincipal Jwt jwt, @PathVariable String notificationId) {
        return notifications.markRead(jwt.getSubject(), notificationId);
    }

    @PatchMapping("/read-all")
    public Map<String, Long> markAllRead(@AuthenticationPrincipal Jwt jwt) {
        return Map.of("updatedCount", notifications.markAllRead(jwt.getSubject()));
    }
}
