package com.devhub.auth;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document("users")
public record UserAccount(
        @Id String id,
        @Indexed(unique = true) String email,
        @Indexed(unique = true, sparse = true) String username,
        String passwordHash,
        RegistrationRole role,
        @Indexed
        String organizationId,
        RegistrationMethod registrationMethod,
        String providerSubject,
        Instant emailVerifiedAt,
        Instant createdAt
) { }
