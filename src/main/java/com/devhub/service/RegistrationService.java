package com.devhub.service;

import com.devhub.auth.OrganizationRegistrationMode;
import com.devhub.auth.ProviderIdentity;
import com.devhub.auth.RegistrationChallenge;
import com.devhub.auth.RegistrationMethod;
import com.devhub.auth.RegistrationProperties;
import com.devhub.auth.RegistrationRole;
import com.devhub.auth.UserAccount;

import com.devhub.repository.RegistrationChallengeRepository;
import com.devhub.dto.RegistrationRequests;
import com.devhub.repository.UserAccountRepository;

import com.devhub.dto.RegistrationRequests.EmailOtpRequest;
import com.devhub.dto.RegistrationRequests.OtpIssuedResponse;
import com.devhub.dto.RegistrationRequests.RegistrationResponse;
import com.devhub.dto.RegistrationRequests.SocialOtpRequest;
import com.devhub.dto.RegistrationRequests.GitHubCodeRegistrationRequest;
import com.devhub.dto.RegistrationRequests.VerifyOtpRequest;
import com.devhub.organization.Organization;
import com.devhub.service.OrganizationService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Locale;
import java.util.NoSuchElementException;
import java.util.UUID;

@Service
public class RegistrationService {
    private final UserAccountRepository users;
    private final RegistrationChallengeRepository challenges;
    private final RegistrationProperties properties;
    private final PasswordEncoder passwordEncoder;
    private final OtpEmailSender emailSender;
    private final SocialIdentityVerifier identityVerifier;
    private final OrganizationService organizations;
    private final SecureRandom random = new SecureRandom();

    public RegistrationService(UserAccountRepository users, RegistrationChallengeRepository challenges,
                               RegistrationProperties properties, PasswordEncoder passwordEncoder,
                               OtpEmailSender emailSender, SocialIdentityVerifier identityVerifier,
                               OrganizationService organizations) {
        this.users = users;
        this.challenges = challenges;
        this.properties = properties;
        this.passwordEncoder = passwordEncoder;
        this.emailSender = emailSender;
        this.identityVerifier = identityVerifier;
        this.organizations = organizations;
    }

    public OtpIssuedResponse requestEmailOtp(EmailOtpRequest request) {
        String email = normalizeEmail(request.email());
        String username = request.username().trim().toLowerCase(Locale.ROOT);
        RegistrationContext context = resolveContext(request.organizationMode(), request.organizationName(),
                request.organizationId(), request.role(), request.companyAccessCode());
        if (users.existsByEmail(email)) throw new IllegalStateException("An account already exists for this email");
        if (users.existsByUsername(username)) throw new IllegalStateException("Username is unavailable");
        checkProductManagerSlot(context);
        String otp = generateOtp();
        RegistrationChallenge challenge = new RegistrationChallenge(UUID.randomUUID().toString(), email, username,
                passwordEncoder.encode(request.password()), context.role(), context.mode(), context.organizationId(), context.organizationName(), RegistrationMethod.EMAIL, null,
                otpHash(otp), 0, Instant.now().plus(properties.otpTtl()), Instant.now());
        challenges.save(challenge);
        emailSender.send(email, otp);
        return new OtpIssuedResponse(challenge.id(), challenge.expiresAt());
    }

    public OtpIssuedResponse requestSocialOtp(RegistrationMethod method,
                                              SocialOtpRequest request) {
        RegistrationContext context = resolveContext(request.organizationMode(),
                request.organizationName(),
                request.organizationId(),
                request.role(),
                request.companyAccessCode());
        ProviderIdentity identity = identityVerifier.verify(method, request.providerToken());
        return createSocialChallenge(method, context, identity);
    }

    public OtpIssuedResponse requestGitHubOtp(GitHubCodeRegistrationRequest request) {
        RegistrationContext context = resolveContext(request.organizationMode(), request.organizationName(),
                request.organizationId(), request.role(), request.companyAccessCode());
        ProviderIdentity identity = identityVerifier.verifyGitHubAuthorizationCode(request.code());
        return createSocialChallenge(RegistrationMethod.GITHUB, context, identity);
    }

    public OtpIssuedResponse requestGoogleOtp(RegistrationRequests.GoogleCodeRegistrationRequest request) {
        RegistrationContext context = resolveContext(request.organizationMode(), request.organizationName(),
                request.organizationId(), request.role(), request.companyAccessCode());
        ProviderIdentity identity = identityVerifier.verifyGoogleAuthorizationCode(request.code());
        return createSocialChallenge(RegistrationMethod.GOOGLE, context, identity);
    }

    private OtpIssuedResponse createSocialChallenge(RegistrationMethod method, RegistrationContext context,
                                                    ProviderIdentity identity) {
        if (users.existsByEmail(identity.email())) throw new IllegalStateException("An account already exists for this email");
        checkProductManagerSlot(context);
        String username = uniqueUsername(identity.username(), identity.email());
        String otp = generateOtp();
        RegistrationChallenge challenge = new RegistrationChallenge(UUID.randomUUID().toString(), identity.email(), username,
                null, context.role(), context.mode(), context.organizationId(), context.organizationName(), method, identity.subject(), otpHash(otp), 0,
                Instant.now().plus(properties.otpTtl()), Instant.now());
        challenges.save(challenge);
        emailSender.send(identity.email(), otp);
        return new OtpIssuedResponse(challenge.id(), challenge.expiresAt());
    }

    public RegistrationResponse verifyOtp(VerifyOtpRequest request) {
        RegistrationChallenge challenge = challenges.findById(request.challengeId())
                .orElseThrow(() -> new NoSuchElementException("Registration challenge was not found or has expired"));
        if (!challenge.expiresAt().isAfter(Instant.now())) {
            challenges.delete(challenge);
            throw new IllegalArgumentException("Verification code expired");
        }
        if (challenge.attempts() >= properties.maxOtpAttempts()) {
            challenges.delete(challenge);
            throw new IllegalArgumentException("Too many verification attempts");
        }
        if (!MessageDigest.isEqual(challenge.otpHash().getBytes(StandardCharsets.UTF_8), otpHash(request.otp()).getBytes(StandardCharsets.UTF_8))) {
            challenges.save(new RegistrationChallenge(challenge.id(), challenge.email(), challenge.username(), challenge.passwordHash(),
                    challenge.role(), challenge.organizationMode(), challenge.organizationId(), challenge.organizationName(), challenge.method(), challenge.providerSubject(), challenge.otpHash(), challenge.attempts() + 1,
                    challenge.expiresAt(), challenge.createdAt()));
            throw new IllegalArgumentException("Verification code is invalid");
        }
        if (users.existsByEmail(challenge.email()) || (challenge.username() != null && users.existsByUsername(challenge.username()))) {
            challenges.delete(challenge);
            throw new IllegalStateException("Email or username is already registered");
        }
        if (challenge.role() == RegistrationRole.PRODUCT_MANAGER
                && users.existsByOrganizationIdAndRole(challenge.organizationId(), RegistrationRole.PRODUCT_MANAGER)) {
            challenges.delete(challenge);
            throw new IllegalStateException("This organization already has a Product Manager");
        }
        Instant verifiedAt = Instant.now();
        UserAccount user = users.save(new UserAccount(null, challenge.email(), challenge.username(), challenge.passwordHash(),
                challenge.role(), challenge.organizationId(), challenge.method(), challenge.providerSubject(), verifiedAt, verifiedAt));
        if (challenge.organizationMode() == OrganizationRegistrationMode.CREATE) {
            try {
                Organization organization = organizations.createForOwner(challenge.organizationName(), user);
                user = users.save(new UserAccount(user.id(), user.email(), user.username(), user.passwordHash(), user.role(),
                        organization.id(), user.registrationMethod(), user.providerSubject(), user.emailVerifiedAt(), user.createdAt()));
            } catch (RuntimeException ex) {
                users.delete(user);
                throw ex;
            }
        }
        challenges.delete(challenge);
        return new RegistrationResponse(user.id(), user.username(), user.email(), user.role(), user.organizationId(), user.registrationMethod(), user.emailVerifiedAt());
    }

    private RegistrationContext resolveContext(OrganizationRegistrationMode mode, String organizationName,
                                               String organizationId, RegistrationRole requestedRole, String accessCode) {
        if (mode == null) throw new IllegalArgumentException("Choose whether to create or join an organization");
        if (mode == OrganizationRegistrationMode.CREATE) {
            if (organizationName == null || organizationName.isBlank() || organizationName.trim().length() > 100) {
                throw new IllegalArgumentException("Organization name must contain 1 to 100 characters");
            }
            if (organizationId != null && !organizationId.isBlank()) {
                throw new IllegalArgumentException("Do not provide an organization ID when creating an organization");
            }
            if (requestedRole != null && requestedRole != RegistrationRole.OWNER) {
                throw new IllegalArgumentException("Creating an organization registers the creator as its owner");
            }
            return new RegistrationContext(mode, null, organizationName.trim(), RegistrationRole.OWNER);
        }
        if (organizationId == null || organizationId.isBlank()) throw new IllegalArgumentException("Organization ID is required to join");
        if (requestedRole == null || requestedRole == RegistrationRole.OWNER) {
            throw new IllegalArgumentException("Choose Product Manager, Senior Manager, or Developer when joining");
        }
        if (organizationName != null && !organizationName.isBlank()) {
            throw new IllegalArgumentException("Do not provide an organization name when joining");
        }
        organizations.validateRoleCode(organizationId.trim(), requestedRole, accessCode);
        return new RegistrationContext(mode, organizationId.trim(), null, requestedRole);
    }

    private record RegistrationContext(OrganizationRegistrationMode mode, String organizationId,
                                       String organizationName, RegistrationRole role) { }

    private void checkProductManagerSlot(RegistrationContext context) {
        if (context.mode() == OrganizationRegistrationMode.JOIN && context.role() == RegistrationRole.PRODUCT_MANAGER
                && users.existsByOrganizationIdAndRole(context.organizationId(), RegistrationRole.PRODUCT_MANAGER)) {
            throw new IllegalStateException("This organization already has a Product Manager");
        }
    }

    private String otpHash(String otp) {
        try {
            if (properties.otpHmacSecret() == null || properties.otpHmacSecret().length() < 32) throw new IllegalStateException("OTP_HMAC_SECRET must be configured with at least 32 characters");
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(properties.otpHmacSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return Base64.getEncoder().encodeToString(mac.doFinal(otp.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.GeneralSecurityException ex) {
            throw new IllegalStateException("OTP hashing is unavailable", ex);
        }
    }

    private String generateOtp() { return String.format(Locale.ROOT, "%06d", random.nextInt(1_000_000)); }
    private String normalizeEmail(String email) { return email.trim().toLowerCase(Locale.ROOT); }
    private String uniqueUsername(String suggested, String email) {
        String base = (suggested == null || suggested.isBlank() ? email.substring(0, email.indexOf('@')) : suggested)
                .toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._-]", "");
        if (base.length() < 3) base = "user";
        base = base.substring(0, Math.min(base.length(), 32));
        String candidate = base;
        for (int i = 1; users.existsByUsername(candidate); i++) candidate = base.substring(0, Math.min(base.length(), 34)) + i;
        return candidate;
    }
}
