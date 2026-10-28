package com.devhub.service;

import com.devhub.auth.ProviderIdentity;
import com.devhub.auth.RegistrationMethod;
import com.devhub.auth.RegistrationProperties;

import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.util.Map;
import java.util.List;
import java.util.Set;
import java.util.Arrays;
import java.util.stream.Collectors;

@Component
public class SocialIdentityVerifier {
    private final RestClient restClient = RestClient.create();
    private final RegistrationProperties properties;
    private final JwtDecoder googleJwtDecoder;

    public SocialIdentityVerifier(RegistrationProperties properties) {
        this.properties = properties;
        NimbusJwtDecoder decoder = NimbusJwtDecoder
                .withJwkSetUri("https://www.googleapis.com/oauth2/v3/certs")
                .build();
        decoder.setJwtValidator(JwtValidators.createDefault());
        this.googleJwtDecoder = decoder;
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

    public ProviderIdentity verifyGitHubAuthorizationCode(String code, String codeVerifier) {
        if (properties.githubClientId() == null || properties.githubClientId().isBlank()
                || properties.githubClientSecret() == null || properties.githubClientSecret().isBlank()) {
            throw new IllegalStateException("GitHub sign-in is not configured");
        }
        if (code == null || code.isBlank()) throw new IllegalArgumentException("GitHub authorization code is required");
        if (codeVerifier == null || codeVerifier.length() < 43 || codeVerifier.length() > 128) {
            throw new IllegalArgumentException("GitHub PKCE code verifier must contain 43 to 128 characters");
        }
        try {
            MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
            form.add("client_id", properties.githubClientId());
            form.add("client_secret", properties.githubClientSecret());
            form.add("code", code);
            form.add("redirect_uri", properties.githubRedirectUri());
            form.add("code_verifier", codeVerifier);
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
        Set<String> audiences = googleClientAudiences();
        if (audiences.isEmpty()) {
            throw new IllegalStateException("Google sign-in is not configured");
        }
        if (idToken == null || idToken.isBlank()) {
            throw new IllegalArgumentException("Google identity token is required");
        }

        final Jwt jwt;
        try {
            jwt = googleJwtDecoder.decode(idToken);
        } catch (JwtException ex) {
            throw new IllegalArgumentException("Google identity token is invalid or expired", ex);
        }

        String issuer = String.valueOf(jwt.getClaims().get("iss"));
        boolean trustedIssuer = issuer.equals("accounts.google.com")
                || issuer.equals("https://accounts.google.com");
        boolean trustedAudience = jwt.getAudience().stream().anyMatch(audiences::contains);
        Object emailVerified = jwt.getClaims().get("email_verified");
        boolean verifiedEmail = Boolean.TRUE.equals(emailVerified)
                || "true".equalsIgnoreCase(String.valueOf(emailVerified));
        if (!trustedIssuer || !trustedAudience || !verifiedEmail) {
            throw new IllegalArgumentException("Google identity token is invalid or email is not verified");
        }
        return verifiedIdentity(jwt.getSubject(), jwt.getClaims().get("email"),
                valueOrEmpty(jwt.getClaims().get("name")));
    }

    private Set<String> googleClientAudiences() {
        String configuredIds = properties.googleClientIds();
        if (configuredIds == null || configuredIds.isBlank()) configuredIds = properties.googleClientId();
        if (configuredIds == null || configuredIds.isBlank()) return Set.of();
        return Arrays.stream(configuredIds.split(","))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
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
