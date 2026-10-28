package com.devhub.controller;

import com.devhub.project.Project;

import com.devhub.dto.ProjectRequests;
import com.devhub.service.ProjectService;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/projects")
public class ProjectController {
    private final ProjectService projects;
    public ProjectController(ProjectService projects) { this.projects = projects; }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Project create(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody ProjectRequests.CreateProjectRequest request) {
        return projects.create(jwt.getSubject(), request);
    }
    @GetMapping
    public List<Project> list(@AuthenticationPrincipal Jwt jwt) { return projects.list(jwt.getSubject()); }
    @GetMapping("/{projectId}")
    public Project get(@AuthenticationPrincipal Jwt jwt, @PathVariable String projectId) { return projects.get(jwt.getSubject(), projectId); }
    @PatchMapping("/{projectId}")
    public Project update(@AuthenticationPrincipal Jwt jwt, @PathVariable String projectId,
                          @Valid @RequestBody ProjectRequests.UpdateProjectRequest request) {
        return projects.update(jwt.getSubject(), projectId, request);
    }
    @PostMapping("/{projectId}/leave")
    public ResponseEntity<Void> leave(@AuthenticationPrincipal Jwt jwt, @PathVariable String projectId) {
        projects.leave(jwt.getSubject(), projectId);
        return ResponseEntity.noContent().build();
    }
}
