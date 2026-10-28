package com.devhub.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties("devhub.registration")
public record RegistrationProperties(
        Duration otpTtl,
        int maxOtpAttempts,
        String otpHmacSecret,
        String googleClientId,
        String googleClientSecret,
        String googleRedirectUri,
        String githubClientId,
        String githubClientSecret,
        String githubRedirectUri
) { }
