package com.devhub.service;

import com.devhub.auth.AuthException;
import com.devhub.auth.ProviderIdentity;
import com.devhub.auth.RegistrationMethod;
import com.devhub.auth.UserAccount;

import com.devhub.dto.AuthTokenResponse;
import com.devhub.dto.LoginRequests;
import com.devhub.repository.UserAccountRepository;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Locale;

@Service
public class LoginService {
    private final UserAccountRepository users;
    private final PasswordEncoder passwordEncoder;
    private final SocialIdentityVerifier identityVerifier;
    private final TokenService tokenService;

    public LoginService(UserAccountRepository users, PasswordEncoder passwordEncoder,
                        SocialIdentityVerifier identityVerifier, TokenService tokenService) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.identityVerifier = identityVerifier;
        this.tokenService = tokenService;
    }

    public AuthTokenResponse loginWithPassword(LoginRequests.PasswordLoginRequest request) {
        String identifier = request.identifier().trim().toLowerCase(Locale.ROOT);
        UserAccount user = users.findByEmailOrUsername(identifier, identifier).orElseThrow(AuthException::new);
        if (user.passwordHash() == null || !passwordEncoder.matches(request.password(), user.passwordHash())) {
            throw new AuthException();
        }
        return tokenService.issue(user);
    }

    public AuthTokenResponse loginWithProvider(RegistrationMethod method, String providerToken) {
        ProviderIdentity identity;
        try {
            identity = identityVerifier.verify(method, providerToken);
        } catch (IllegalArgumentException ex) {
            throw new AuthException();
        }
        UserAccount user = users.findByRegistrationMethodAndProviderSubject(method, identity.subject())
                .orElseThrow(AuthException::new);
        return tokenService.issue(user);
    }

    public AuthTokenResponse loginWithGitHubCode(String code) {
        ProviderIdentity identity;
        try {
            identity = identityVerifier.verifyGitHubAuthorizationCode(code);
        } catch (IllegalArgumentException ex) {
            throw new AuthException();
        }
        UserAccount user = users.findByRegistrationMethodAndProviderSubject(RegistrationMethod.GITHUB, identity.subject())
                .orElseThrow(AuthException::new);
        return tokenService.issue(user);
    }

    public AuthTokenResponse loginWithGoogleCode(String code) {
        ProviderIdentity identity;
        try {
            identity = identityVerifier.verifyGoogleAuthorizationCode(code);
        } catch (IllegalArgumentException ex) {
            throw new AuthException();
        }
        UserAccount user = users.findByRegistrationMethodAndProviderSubject(RegistrationMethod.GOOGLE, identity.subject())
                .orElseThrow(AuthException::new);
        return tokenService.issue(user);
    }

    public AuthTokenResponse refresh(String refreshToken) { return tokenService.refresh(refreshToken); }
    public void logout(String refreshToken) { tokenService.revoke(refreshToken); }
}
