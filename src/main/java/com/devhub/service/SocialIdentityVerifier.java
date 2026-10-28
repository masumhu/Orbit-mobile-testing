package com.devhub.service;

import com.devhub.auth.ProviderIdentity;
import com.devhub.auth.RegistrationMethod;
import com.devhub.auth.RegistrationProperties;

import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.util.Map;
import java.util.List;

@Component
public class SocialIdentityVerifier {
    private final RestClient restClient = RestClient.create();
    private final RegistrationProperties properties;

    public SocialIdentityVerifier(RegistrationProperties properties) {
        this.properties = properties;
    }

    public ProviderIdentity verify(RegistrationMethod method, String token) {
        try {
            return switch (method) {
                case GOOGLE -> verifyGoogle(token);
                case GITHUB -> verifyGitHub(token);
                default -> throw new IllegalArgumentException("Unsupported social registration method");
            };
        } catch (RestClientException ex) {
            throw new IllegalArgumentException("Could not verify identity with the selected provider");
        }
    }

    public ProviderIdentity verifyGitHubAuthorizationCode(String code) {
        if (properties.githubClientId() == null || properties.githubClientId().isBlank()
                || properties.githubClientSecret() == null || properties.githubClientSecret().isBlank()) {
            throw new IllegalStateException("GitHub sign-in is not configured");
        }
        if (code == null || code.isBlank()) throw new IllegalArgumentException("GitHub authorization code is required");
        try {
            MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
            form.add("client_id", properties.githubClientId());
            form.add("client_secret", properties.githubClientSecret());
            form.add("code", code);
            form.add("redirect_uri", properties.githubRedirectUri());
            Map<?, ?> tokenResponse = restClient.post()
                    .uri("https://github.com/login/oauth/access_token")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(form)
                    .retrieve().body(Map.class);
            Object accessToken = tokenResponse == null ? null : tokenResponse.get("access_token");
            if (!(accessToken instanceof String token) || token.isBlank()) {
                throw new IllegalArgumentException("GitHub did not return an access token");
            }
            return verifyGitHub(token);
        } catch (RestClientException ex) {
            throw new IllegalArgumentException("Could not complete GitHub sign-in");
        }
    }

    public ProviderIdentity verifyGoogleAuthorizationCode(String code) {
        if (properties.googleClientId() == null || properties.googleClientId().isBlank()
                || properties.googleClientSecret() == null || properties.googleClientSecret().isBlank()) {
            throw new IllegalStateException("Google authorization-code sign-in is not configured");
        }
        if (code == null || code.isBlank()) throw new IllegalArgumentException("Google authorization code is required");
        try {
            MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
            form.add("client_id", properties.googleClientId());
            form.add("client_secret", properties.googleClientSecret());
            form.add("code", code);
            form.add("redirect_uri", properties.googleRedirectUri());
            form.add("grant_type", "authorization_code");
            Map<?, ?> tokenResponse = restClient.post()
                    .uri("https://oauth2.googleapis.com/token")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve().body(Map.class);
            Object idToken = tokenResponse == null ? null : tokenResponse.get("id_token");
            if (!(idToken instanceof String token) || token.isBlank()) {
                throw new IllegalArgumentException("Google did not return an identity token");
            }
            return verifyGoogle(token);
        } catch (RestClientException ex) {
            throw new IllegalArgumentException("Could not complete Google sign-in");
        }
    }

    private ProviderIdentity verifyGoogle(String idToken) {
        if (properties.googleClientId() == null || properties.googleClientId().isBlank()) {
            throw new IllegalStateException("Google sign-in is not configured");
        }
        Map<?, ?> claims = restClient.get()
                .uri("https://oauth2.googleapis.com/tokeninfo?id_token={token}", idToken)
                .retrieve().body(Map.class);
        if (claims == null || !properties.googleClientId().equals(claims.get("aud")) || !"true".equals(String.valueOf(claims.get("email_verified")))) {
            throw new IllegalArgumentException("Google identity token is invalid or email is not verified");
        }
        return verifiedIdentity(claims.get("sub"), claims.get("email"), valueOrEmpty(claims.get("name")));
    }

    private ProviderIdentity verifyGitHub(String accessToken) {
        if (accessToken == null || accessToken.isBlank()) throw new IllegalArgumentException("GitHub access token is required");
        Map<?, ?> profile = restClient.get()
                .uri("https://api.github.com/user")
                .header("Authorization", "Bearer " + accessToken)
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "Orbit")
                .retrieve().body(Map.class);
        if (profile == null) throw new IllegalArgumentException("GitHub did not return a profile");

        Object emailClaim = profile.get("email");
        String email = emailClaim == null ? null : emailClaim.toString();
        if (email == null || email.isBlank()) {
            List<?> emails = restClient.get()
                    .uri("https://api.github.com/user/emails")
                    .header("Authorization", "Bearer " + accessToken)
                    .header("Accept", "application/vnd.github+json")
                    .header("User-Agent", "Orbit")
                    .retrieve().body(List.class);
            if (emails != null) {
                email = emails.stream().filter(Map.class::isInstance).map(Map.class::cast)
                        .filter(value -> Boolean.TRUE.equals(value.get("primary")) && Boolean.TRUE.equals(value.get("verified")))
                        .map(value -> value.get("email")).filter(String.class::isInstance).map(String.class::cast)
                        .findFirst().orElse(null);
            }
        }
        return verifiedIdentity(profile.get("id"), email, valueOrEmpty(profile.get("login")));
    }

    private ProviderIdentity verifiedIdentity(Object subject, Object email, String username) {
        if (subject == null || subject.toString().isBlank() || email == null || !email.toString().contains("@")) {
            throw new IllegalArgumentException("Provider did not return a verified account identity");
        }
        return new ProviderIdentity(subject.toString(), email.toString().trim().toLowerCase(java.util.Locale.ROOT), username);
    }

    private String valueOrEmpty(Object value) {
         return value == null ? "" : value.toString(); }
}
