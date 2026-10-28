package com.devhub.auth;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document("refresh_sessions")
public record RefreshTokenSession(
        @Id String id,
        String familyId,
        String userId,
        @Indexed(unique = true) String tokenHash,
        Instant createdAt,
        Instant revokedAt,
        @Indexed(expireAfter = "0s") Instant expiresAt
) { }
