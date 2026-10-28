package com.devhub.controller;

import com.devhub.task.Task;

import com.devhub.dto.TaskRequests;
import com.devhub.service.TaskService;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
public class TaskController {
    private final TaskService tasks;
    public TaskController(TaskService tasks) { this.tasks = tasks; }

    @PostMapping("/api/v1/projects/{projectId}/tasks")
    @ResponseStatus(HttpStatus.CREATED)
    public Task create(@AuthenticationPrincipal Jwt jwt, @PathVariable String projectId,
                       @Valid @RequestBody TaskRequests.CreateTaskRequest request) {
        return tasks.create(jwt.getSubject(), projectId, request);
    }
    @GetMapping("/api/v1/projects/{projectId}/tasks")
    public List<Task> list(@AuthenticationPrincipal Jwt jwt, @PathVariable String projectId) { return tasks.list(jwt.getSubject(), projectId); }
    @GetMapping("/api/v1/tasks/{taskId}")
    public Task get(@AuthenticationPrincipal Jwt jwt, @PathVariable String taskId) { return tasks.get(jwt.getSubject(), taskId); }
    @PutMapping("/api/v1/tasks/{taskId}/assignee")
    public Task assign(@AuthenticationPrincipal Jwt jwt, @PathVariable String taskId,
                       @Valid @RequestBody TaskRequests.AssignTaskRequest request) { return tasks.assign(jwt.getSubject(), taskId, request); }
    @PatchMapping("/api/v1/tasks/{taskId}/status")
    public Task status(@AuthenticationPrincipal Jwt jwt, @PathVariable String taskId,
                       @Valid @RequestBody TaskRequests.UpdateTaskStatusRequest request) { return tasks.updateStatus(jwt.getSubject(), taskId, request); }
}
