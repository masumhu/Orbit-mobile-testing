package com.devhub.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties("devhub.security")
public record SecurityTokenProperties(String jwtSecret, Duration accessTokenTtl, Duration refreshTokenTtl) { }
