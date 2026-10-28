package com.devhub.chat;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.Instant;

@Document("chat_messages")
public record ChatMessage(@Id String id, String channelId, String authorUserId, String body,
                          String parentMessageId, Instant createdAt, Instant updatedAt, Instant deletedAt) { }
