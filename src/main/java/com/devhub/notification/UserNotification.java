package com.devhub.notification;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document("notifications")
public record UserNotification(@Id String id,
                               String recipientUserId,
                               String organizationId,
                               String type,
                               String title,
                               String message,
                               String relatedEntityType,
                               String relatedEntityId,
                               Instant createdAt,
                               Instant readAt) { }
