package com.devhub.repository;

import com.devhub.notification.UserNotification;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface UserNotificationRepository extends MongoRepository<UserNotification, String> {
    List<UserNotification> findByRecipientUserIdOrderByCreatedAtDesc(String recipientUserId);
    List<UserNotification> findByRecipientUserIdAndReadAtIsNull(String recipientUserId);
    long countByRecipientUserIdAndReadAtIsNull(String recipientUserId);
}
