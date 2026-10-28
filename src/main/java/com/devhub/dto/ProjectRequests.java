package com.devhub.dto;

import com.devhub.project.ProjectStatus;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.time.LocalDate;

public final class ProjectRequests {
    private ProjectRequests() { }
    public record CreateProjectRequest(@NotBlank @Size(max = 120) String name, @Size(max = 4000) String description,
                                       List<String> seniorManagerIds, LocalDate deadline) { }
    public record UpdateProjectRequest(@NotBlank @Size(max = 120) String name, @Size(max = 4000) String description,
                                       List<String> seniorManagerIds, ProjectStatus status, LocalDate deadline) { }
}
