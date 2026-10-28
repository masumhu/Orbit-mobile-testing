package com.devhub.project;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

@Document("projects")
public record Project(
        @Id String id,
        String organizationId,
        String name,
        String description,
        String productManagerId,
        List<String> seniorManagerIds,
        List<String> developerIds,
        ProjectStatus status,
        Instant createdAt,
        Instant updatedAt,
        LocalDate deadline
) { }
