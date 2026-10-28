package com.devhub.controller;

import com.devhub.auth.RegistrationMethod;

import com.devhub.dto.AuthTokenResponse;
import com.devhub.dto.LoginRequests;
import com.devhub.service.LoginService;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
public class LoginController {
    private final LoginService loginService;

    public LoginController(LoginService loginService) {
        this.loginService = loginService;
    }

    @PostMapping("/login")
    public AuthTokenResponse login(@Valid @RequestBody LoginRequests
            .PasswordLoginRequest request) {
        return loginService.loginWithPassword(request);
    }

    @PostMapping("/login/{provider}")
    public AuthTokenResponse socialLogin(@PathVariable String provider, @Valid @RequestBody LoginRequests.SocialLoginRequest request) {
        RegistrationMethod method = switch (provider.toLowerCase()) {
            case "google" -> RegistrationMethod.GOOGLE;
            case "github" -> RegistrationMethod.GITHUB;
            default -> throw new IllegalArgumentException("Provider must be google or github");
        };
        return loginService.loginWithProvider(method, request.providerToken());
    }

    @PostMapping("/login/github/callback")
    public AuthTokenResponse githubCallback(@Valid @RequestBody LoginRequests.GitHubCodeLoginRequest request) {
        return loginService.loginWithGitHubCode(request.code());
    }

    @PostMapping("/login/google/callback")
    public AuthTokenResponse googleCallback(@Valid @RequestBody LoginRequests.GoogleCodeLoginRequest request) {
        return loginService.loginWithGoogleCode(request.code());
    }

    @PostMapping("/refresh")
    public AuthTokenResponse refresh(@Valid @RequestBody LoginRequests.RefreshRequest request) {
        return loginService.refresh(request.refreshToken());
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@Valid @RequestBody LoginRequests.RefreshRequest request) {
        loginService.logout(request.refreshToken());
    }
}
