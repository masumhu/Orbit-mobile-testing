package com.devhub.task;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.Instant;
import java.time.LocalDate;

@Document("tasks")
public record Task(@Id String id, String organizationId, String projectId, String title, String description,
                   String createdBySeniorManagerId, String assignedDeveloperId, TaskStatus status,
                   Instant createdAt, Instant updatedAt, LocalDate deadline) { }
