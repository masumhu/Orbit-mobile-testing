package com.devhub.controller;

import com.devhub.dto.AuthUserResponse;
import com.devhub.repository.UserAccountRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class CurrentUserController {
    private final UserAccountRepository users;

    public CurrentUserController(UserAccountRepository users) {
        this.users = users;
    }

    @GetMapping({"/api/auth/me", "/api/v1/auth/me"})
    public AuthUserResponse currentUser(@AuthenticationPrincipal Jwt jwt) {
        var user = users.findById(jwt.getSubject())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User was not found"));
        return new AuthUserResponse(user.id(), user.username(), user.email(), user.role(), user.organizationId());
    }
}
