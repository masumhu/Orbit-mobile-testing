package com.devhub.service;

import com.devhub.notification.UserNotification;
import com.devhub.repository.UserNotificationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;

@Service
public class UserNotificationService {
    private static final Logger log = LoggerFactory.getLogger(UserNotificationService.class);
    private final UserNotificationRepository notifications;

    public UserNotificationService(UserNotificationRepository notifications) {
        this.notifications = notifications;
    }

    public void notifyUser(String recipientUserId, String organizationId, String type,
                           String title, String message, String relatedEntityType, String relatedEntityId) {
        if (recipientUserId == null || recipientUserId.isBlank()) return;
        try {
            notifications.save(new UserNotification(null, recipientUserId, organizationId, type, title,
                    message, relatedEntityType, relatedEntityId, Instant.now(), null));
        } catch (RuntimeException ex) {
            // A notification failure should not make the underlying task or review action appear to fail.
            log.warn("Could not save {} notification for user {}", type, recipientUserId, ex);
        }
    }

    public List<UserNotification> list(String recipientUserId) {
        return notifications.findByRecipientUserIdOrderByCreatedAtDesc(recipientUserId);
    }

    public long unreadCount(String recipientUserId) {
        return notifications.countByRecipientUserIdAndReadAtIsNull(recipientUserId);
    }

    public UserNotification markRead(String recipientUserId, String notificationId) {
        UserNotification notification = notifications.findById(notificationId)
                .orElseThrow(() -> new NoSuchElementException("Notification was not found"));
        requireRecipient(notification, recipientUserId);
        if (notification.readAt() != null) return notification;
        return notifications.save(new UserNotification(notification.id(), notification.recipientUserId(),
                notification.organizationId(), notification.type(), notification.title(), notification.message(),
                notification.relatedEntityType(), notification.relatedEntityId(), notification.createdAt(), Instant.now()));
    }

    public long markAllRead(String recipientUserId) {
        List<UserNotification> unread = notifications.findByRecipientUserIdAndReadAtIsNull(recipientUserId);
        Instant now = Instant.now();
        List<UserNotification> updated = unread.stream().map(notification -> new UserNotification(notification.id(),
                notification.recipientUserId(), notification.organizationId(), notification.type(), notification.title(),
                notification.message(), notification.relatedEntityType(), notification.relatedEntityId(),
                notification.createdAt(), now)).toList();
        notifications.saveAll(updated);
        return updated.size();
    }

    private void requireRecipient(UserNotification notification, String recipientUserId) {
        if (!notification.recipientUserId().equals(recipientUserId)) {
            throw new AccessDeniedException("You can only access your own notifications");
        }
    }
}
