package com.devhub.dto;

import com.devhub.task.TaskStatus;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public final class TaskRequests {
    private TaskRequests() { }
    public record CreateTaskRequest(@NotBlank @Size(max=160) String title, @Size(max=5000) String description,
                                   LocalDate deadline) { }
    public record AssignTaskRequest(@NotBlank String developerId) { }
    public record UpdateTaskStatusRequest(@NotNull TaskStatus status) { }
}
