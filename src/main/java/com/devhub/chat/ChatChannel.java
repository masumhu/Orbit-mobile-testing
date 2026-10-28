package com.devhub.chat;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.Instant;
import java.util.List;

@Document("chat_channels")
public record ChatChannel(@Id String id, String organizationId, String projectId, ChannelScope scope,
                          String name, String createdByUserId, List<String> memberIds,
                          Instant createdAt, Instant updatedAt) { }
