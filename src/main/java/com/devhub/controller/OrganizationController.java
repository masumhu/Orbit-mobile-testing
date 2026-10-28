package com.devhub.controller;

import com.devhub.dto.OrganizationRequests;
import com.devhub.service.OrganizationService;

import com.devhub.dto.OrganizationRequests.OrganizationResponse;
import com.devhub.dto.OrganizationRequests.UpdateAccessCodesRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/organizations")
public class OrganizationController {
    private final OrganizationService organizations;

    public OrganizationController(OrganizationService organizations) {
        this.organizations = organizations;
    }

    @GetMapping("/{organizationId}")
    public OrganizationResponse view(@PathVariable String organizationId, @AuthenticationPrincipal Jwt jwt) {
        return organizations.view(organizationId, jwt.getSubject());
    }

    @GetMapping("/{organizationId}/members")
    public java.util.List<OrganizationRequests.MemberResponse> members(@PathVariable String organizationId, @AuthenticationPrincipal Jwt jwt) {
        return organizations.members(organizationId, jwt.getSubject());
    }

    @GetMapping("/{organizationId}/developers")
    public java.util.List<OrganizationRequests.MemberResponse> developers(@PathVariable String organizationId,
                                                                           @AuthenticationPrincipal Jwt jwt) {
        return organizations.developers(organizationId, jwt.getSubject());
    }

    @GetMapping("/{organizationId}/senior-managers")
    public java.util.List<OrganizationRequests.MemberResponse> seniorManagers(@PathVariable String organizationId,
                                                                               @AuthenticationPrincipal Jwt jwt) {
        return organizations.seniorManagers(organizationId, jwt.getSubject());
    }

    @PutMapping("/{organizationId}/access-codes")
    @ResponseStatus(HttpStatus.OK)
    public OrganizationResponse updateAccessCodes(@PathVariable String organizationId,
                                                   @AuthenticationPrincipal Jwt jwt,
                                                   @Valid @RequestBody UpdateAccessCodesRequest request) {
        return organizations.updateAccessCodes(organizationId, jwt.getSubject(), request);
    }

    @PostMapping("/{organizationId}/leave")
    public ResponseEntity<Void> leave(@PathVariable String organizationId, @AuthenticationPrincipal Jwt jwt) {
        organizations.leave(organizationId, jwt.getSubject());
        return ResponseEntity.noContent().build();
    }
}
