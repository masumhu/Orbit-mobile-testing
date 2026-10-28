package com.devhub.dto;

import com.devhub.auth.RegistrationRole;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class OrganizationRequests {
    private OrganizationRequests() { }

    public record UpdateAccessCodesRequest(
            @NotBlank @Size(min = 8, max = 128) String productManagerCode,
            @NotBlank @Size(min = 8, max = 128) String seniorManagerCode,
            @NotBlank @Size(min = 8, max = 128) String developerCode
    ) { }

    public record OrganizationResponse(String id, String name, String ownerUserId, boolean accessCodesConfigured) { }
    public record MemberResponse(String id, String username, String email, com.devhub.auth.RegistrationRole role) { }
}
