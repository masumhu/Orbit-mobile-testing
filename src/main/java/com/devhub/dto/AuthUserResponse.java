package com.devhub.dto;

import com.devhub.auth.RegistrationRole;

public record AuthUserResponse(String id, String username, String email, RegistrationRole role, String organizationId) { }
