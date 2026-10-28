package com.devhub.dto;

import com.devhub.auth.OrganizationRegistrationMode;
import com.devhub.auth.RegistrationMethod;
import com.devhub.auth.RegistrationRole;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class RegistrationRequests {
    private RegistrationRequests() { }

    public record EmailOtpRequest(
            @NotNull OrganizationRegistrationMode organizationMode,
            String organizationName,
            String organizationId,
            RegistrationRole role,
            @NotBlank @Size(min = 3, max = 40) String username,
            @NotBlank @Email @Size(max = 254) String email,
            @NotBlank @Size(min = 12, max = 128) String password,
            String companyAccessCode
    ) { }

    public record SocialOtpRequest(
            @NotNull OrganizationRegistrationMode organizationMode,
            String organizationName,
            String organizationId,
            RegistrationRole role,
            @NotBlank String providerToken,
            String companyAccessCode
    ) { }

    public record GitHubCodeRegistrationRequest(
            @NotNull OrganizationRegistrationMode organizationMode,
            String organizationName,
            String organizationId,
            RegistrationRole role,
            @NotBlank String code,
            String companyAccessCode
    ) { }

    public record GoogleCodeRegistrationRequest(
            @NotNull OrganizationRegistrationMode organizationMode,
            String organizationName,
            String organizationId,
            RegistrationRole role,
            @NotBlank String code,
            String companyAccessCode
    ) { }

    public record VerifyOtpRequest(@NotBlank String challengeId, @NotBlank @Size(min = 6, max = 6) String otp) { }
    public record OtpIssuedResponse(String challengeId, java.time.Instant expiresAt) { }
    public record RegistrationResponse(String id, String username, String email, RegistrationRole role, String organizationId, RegistrationMethod method, java.time.Instant emailVerifiedAt) { }
}
