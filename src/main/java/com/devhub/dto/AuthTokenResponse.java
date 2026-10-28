package com.devhub.dto;

import java.time.Instant;

public record AuthTokenResponse(
        String accessToken,
        String tokenType,
        long expiresIn,
        String refreshToken,
        Instant refreshTokenExpiresAt,
        AuthUserResponse user
) { }
