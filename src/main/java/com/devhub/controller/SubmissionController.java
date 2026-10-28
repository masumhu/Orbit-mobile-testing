package com.devhub.controller;

import com.devhub.submission.CodeSubmission;

import com.devhub.dto.SubmissionRequests;
import com.devhub.service.SubmissionService;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
public class SubmissionController {
    private final SubmissionService submissions;
    public SubmissionController(SubmissionService submissions) { this.submissions = submissions; }

    @PostMapping("/api/v1/tasks/{taskId}/submissions")
    @ResponseStatus(HttpStatus.CREATED)
    public CodeSubmission submit(@AuthenticationPrincipal Jwt jwt, @PathVariable String taskId,
                                 @RequestBody @Valid SubmissionRequests.CreateSubmissionRequest request) {
        return submissions.submit(jwt.getSubject(), taskId, request);
    }
    @GetMapping("/api/v1/projects/{projectId}/submissions")
    public List<CodeSubmission> list(@AuthenticationPrincipal Jwt jwt, @PathVariable String projectId) {
        return submissions.listProject(jwt.getSubject(), projectId); }
    @PostMapping("/api/v1/submissions/{submissionId}/forward")
    public CodeSubmission forward(@AuthenticationPrincipal Jwt jwt, @PathVariable String submissionId) {
        return submissions.forward(jwt.getSubject(), submissionId); }
    @PostMapping("/api/v1/submissions/{submissionId}/request-changes")
    public CodeSubmission requestChanges(@AuthenticationPrincipal Jwt jwt, @PathVariable String submissionId,
                                         @RequestBody @Valid SubmissionRequests.RequestChangesRequest request) {
        return submissions.requestChanges(jwt.getSubject(), submissionId, request);
    }
    @PostMapping("/api/v1/submissions/{submissionId}/review")
    public CodeSubmission review(@AuthenticationPrincipal Jwt jwt, @PathVariable String submissionId,
                                 @RequestBody @Valid SubmissionRequests.ReviewSubmissionRequest request) {
        return submissions.review(jwt.getSubject(), submissionId, request);
    }
}
