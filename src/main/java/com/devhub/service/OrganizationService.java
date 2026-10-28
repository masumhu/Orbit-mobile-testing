package com.devhub.service;

import com.devhub.organization.Organization;

import com.devhub.repository.OrganizationRepository;
import com.devhub.dto.OrganizationRequests;

import com.devhub.auth.RegistrationRole;
import com.devhub.auth.UserAccount;
import com.devhub.repository.UserAccountRepository;
import com.devhub.dto.OrganizationRequests.OrganizationResponse;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Locale;
import java.util.NoSuchElementException;
import java.util.UUID;

@Service
public class OrganizationService {
    private final OrganizationRepository organizations;
    private final UserAccountRepository users;
    private final PasswordEncoder passwordEncoder;

    public OrganizationService(OrganizationRepository organizations, UserAccountRepository users, PasswordEncoder passwordEncoder) {
        this.organizations = organizations;
        this.users = users;
        this.passwordEncoder = passwordEncoder;
    }

    public Organization createForOwner(String name, UserAccount owner) {
        Instant now = Instant.now();
        String normalizedName = normalizeName(name);
        Organization organization = new Organization("org_" + UUID.randomUUID(), normalizedName,
                createSlug(normalizedName), owner.id(), null, null, null, now, now);
        return organizations.save(organization);
    }

    public OrganizationResponse view(String organizationId, String userId) {
        Organization organization = find(organizationId);
        requireMember(organization, userId);
        return response(organization);
    }

    public java.util.List<OrganizationRequests.MemberResponse> members(String organizationId, String userId) {
        Organization organization = find(organizationId);
        requireMember(organization, userId);
        requireRosterViewer(userId);
        return users.findByOrganizationId(organizationId).stream()
                .map(user -> new OrganizationRequests.MemberResponse(user.id(), user.username(), user.email(), user.role())).toList();
    }

    public java.util.List<OrganizationRequests.MemberResponse> developers(String organizationId, String userId) {
        return membersByRole(organizationId, userId, RegistrationRole.DEVELOPER);
    }

    public java.util.List<OrganizationRequests.MemberResponse> seniorManagers(String organizationId, String userId) {
        return membersByRole(organizationId, userId, RegistrationRole.SENIOR_MANAGER);
    }

    private java.util.List<OrganizationRequests.MemberResponse> membersByRole(String organizationId, String userId, RegistrationRole role) {
        Organization organization = find(organizationId);
        requireMember(organization, userId);
        requireRosterViewer(userId);
        return users.findByOrganizationId(organizationId).stream()
                .filter(user -> user.role() == role)
                .map(user -> new OrganizationRequests.MemberResponse(user.id(), user.username(), user.email(), user.role())).toList();
    }

    private void requireRosterViewer(String userId) {
        UserAccount requester = users.findById(userId).orElseThrow(() -> new NoSuchElementException("User was not found"));
        if (requester.role() == RegistrationRole.DEVELOPER) {
            throw new IllegalArgumentException("Only the owner, Product Managers, and Senior Managers can view the organization roster");
        }
    }

    public void leave(String organizationId, String userId) {
        Organization organization = find(organizationId);
        UserAccount user = users.findById(userId).orElseThrow(() -> new NoSuchElementException("User was not found"));
        if (!organization.id().equals(user.organizationId())) throw new IllegalArgumentException("You do not belong to this organization");
        if (user.role() == RegistrationRole.OWNER || organization.ownerUserId().equals(user.id())) {
            throw new IllegalArgumentException("The owner must transfer ownership before leaving the organization");
        }
        users.save(new UserAccount(user.id(), user.email(), user.username(), user.passwordHash(), user.role(), null,
                user.registrationMethod(), user.providerSubject(), user.emailVerifiedAt(), user.createdAt()));
    }

    public OrganizationResponse updateAccessCodes(String organizationId, String userId, OrganizationRequests.UpdateAccessCodesRequest request) {
        Organization organization = find(organizationId);
        requireOwner(organization, userId);
        String pm = request.productManagerCode().trim();
        String senior = request.seniorManagerCode().trim();
        String developer = request.developerCode().trim();
        if (pm.length() < 8 || senior.length() < 8 || developer.length() < 8) {
            throw new IllegalArgumentException("Each role access code must contain at least 8 non-space characters");
        }
        if (same(pm, senior) || same(pm, developer) || same(senior, developer)) {
            throw new IllegalArgumentException("Each role access code must be different");
        }
        Organization updated = new Organization(organization.id(), organization.name(), organization.slug(),
                organization.ownerUserId(), passwordEncoder.encode(pm), passwordEncoder.encode(senior),
                passwordEncoder.encode(developer), organization.createdAt(), Instant.now());
        return response(organizations.save(updated));
    }

    public void validateRoleCode(String organizationId, RegistrationRole role, String suppliedCode) {
        Organization organization = find(organizationId);
        if (organization.ownerUserId() == null || !accessCodesConfigured(organization)) {
            throw new IllegalStateException("The organization owner has not configured all role access codes yet");
        }
        String expectedHash = switch (role) {
            case PRODUCT_MANAGER -> organization.productManagerAccessCodeHash();
            case SENIOR_MANAGER -> organization.seniorManagerAccessCodeHash();
            case DEVELOPER -> organization.developerAccessCodeHash();
            case OWNER -> throw new IllegalArgumentException("Owner registration is only available when creating an organization");
        };
        if (suppliedCode == null || !passwordEncoder.matches(suppliedCode.trim(), expectedHash)) {
            throw new IllegalArgumentException("A valid access code for this organization and role is required");
        }
    }

    public Organization find(String organizationId) {
        return organizations.findById(organizationId)
                .orElseThrow(() -> new NoSuchElementException("Organization was not found"));
    }

    private void requireOwner(Organization organization, String userId) {
        UserAccount user = users.findById(userId).orElseThrow(() -> new NoSuchElementException("User was not found"));
        if (!organization.ownerUserId().equals(user.id()) || !organization.id().equals(user.organizationId())
                || user.role() != RegistrationRole.OWNER) {
            throw new IllegalArgumentException("Only this organization's owner can change role access codes");
        }
    }

    private void requireMember(Organization organization, String userId) {
        UserAccount user = users.findById(userId).orElseThrow(() -> new NoSuchElementException("User was not found"));
        if (!organization.id().equals(user.organizationId())) {
            throw new IllegalArgumentException("You do not belong to this organization");
        }
    }

    private OrganizationResponse response(Organization organization) {
        return new OrganizationResponse(organization.id(), organization.name(), organization.ownerUserId(), accessCodesConfigured(organization));
    }

    private boolean accessCodesConfigured(Organization organization) {
        return hasText(organization.productManagerAccessCodeHash())
                && hasText(organization.seniorManagerAccessCodeHash())
                && hasText(organization.developerAccessCodeHash());
    }

    private String normalizeName(String name) {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("Organization name is required");
        return name.trim();
    }

    private String createSlug(String name) {
        String base = name.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-|-$", "");
        if (base.isBlank()) base = "organization";
        if (base.length() > 64) base = base.substring(0, 64).replaceAll("-+$", "");
        return base + "-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }

    private boolean same(String left, String right) {
        return MessageDigest.isEqual(left.toLowerCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8),
                right.toLowerCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8));
    }

    private boolean hasText(String value) { return value != null && !value.isBlank(); }
}
