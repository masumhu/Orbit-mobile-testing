package com.devhub.auth;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document("registration_challenges")
public record RegistrationChallenge(
        @Id String id,
        String email,
        String username,
        String passwordHash,
        RegistrationRole role,
        OrganizationRegistrationMode organizationMode,
        String organizationId,
        String organizationName,
        RegistrationMethod method,
        String providerSubject,
        String otpHash,
        int attempts,
        @Indexed(expireAfter = "0s") Instant expiresAt,
        Instant createdAt
) { }
