package com.devhub.dto;

import jakarta.validation.constraints.NotBlank;

public final class LoginRequests {
    private LoginRequests() { }

    public record PasswordLoginRequest(@NotBlank String identifier, @NotBlank String password) { }
    public record SocialLoginRequest(@NotBlank String providerToken) { }
    public record GitHubCodeLoginRequest(@NotBlank String code) { }
    public record GoogleCodeLoginRequest(@NotBlank String code) { }
    public record RefreshRequest(@NotBlank String refreshToken) { }
}
