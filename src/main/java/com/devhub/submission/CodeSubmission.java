package com.devhub.submission;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.Instant;

@Document("code_submissions")
public record CodeSubmission(@Id String id, String organizationId,
                             String projectId, String taskId,
                             String developerId, String repositoryUrl,
                             String branch, String commitSha,
                             String pullRequestUrl, String storageReference,
                             String reviewNote,
                             SubmissionStatus status, Instant createdAt,
                             Instant updatedAt) { }
