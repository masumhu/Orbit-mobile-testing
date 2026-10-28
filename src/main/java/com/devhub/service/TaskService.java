package com.devhub.service;

import com.devhub.task.Task;
import com.devhub.task.TaskStatus;

import com.devhub.repository.TaskRepository;
import com.devhub.dto.TaskRequests;

import com.devhub.auth.RegistrationRole;
import com.devhub.auth.UserAccount;
import com.devhub.project.Project;
import com.devhub.repository.ProjectRepository;
import com.devhub.service.ProjectService;
import org.springframework.stereotype.Service;
import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;

@Service
public class TaskService {
    private final TaskRepository tasks;
    private final ProjectService projects;
    private final ProjectRepository projectRepository;
    private final UserNotificationService notifications;

    public TaskService(TaskRepository tasks, ProjectService projects, ProjectRepository projectRepository,
                       UserNotificationService notifications) {
        this.tasks = tasks;
        this.projects = projects;
        this.projectRepository = projectRepository;
        this.notifications = notifications;
    }

    public Task create(String userId, String projectId, TaskRequests.CreateTaskRequest request) {
        UserAccount senior = projects.requireRole(userId, RegistrationRole.SENIOR_MANAGER);
        Project project = projects.find(projectId);
        projects.requireView(senior, project);
        if (!project.seniorManagerIds().contains(senior.id())) throw new IllegalArgumentException("You are not assigned to this project");
        Instant now = Instant.now();
        return tasks.save(new Task(null, project.organizationId(), project.id(), request.title().trim(), clean(request.description()),
                senior.id(), null, TaskStatus.TODO, now, now, request.deadline()));
    }

    public List<Task> list(String userId, String projectId) {
        Project project = projects.find(projectId);
        UserAccount user = projects.user(userId);
        projects.requireView(user, project);
        if (user.role() == RegistrationRole.DEVELOPER) return tasks.findByProjectId(projectId).stream()
                .filter(task -> user.id().equals(task.assignedDeveloperId())).toList();
        if (user.role() == RegistrationRole.SENIOR_MANAGER) return tasks.findByProjectId(projectId).stream()
                .filter(task -> user.id().equals(task.createdBySeniorManagerId())).toList();
        return tasks.findByProjectId(projectId);
    }

    public Task assign(String userId, String taskId, TaskRequests.AssignTaskRequest request) {
        Task task = find(taskId);
        Project project = projects.find(task.projectId());
        UserAccount senior = projects.requireRole(userId, RegistrationRole.SENIOR_MANAGER);
        projects.requireView(senior, project);
        if (!task.createdBySeniorManagerId().equals(senior.id())) throw new IllegalArgumentException("Only the task's Senior Manager can assign it");
        if (task.status() == TaskStatus.COMPLETED || task.status() == TaskStatus.CANCELLED) throw new IllegalArgumentException("Completed or cancelled tasks cannot be reassigned");
        UserAccount developer = projects.user(request.developerId());
        if (developer.role() != RegistrationRole.DEVELOPER || !project.organizationId().equals(developer.organizationId())) {
            throw new IllegalArgumentException("Developer must belong to this organization");
        }
        List<String> developerIds = new java.util.ArrayList<>(project.developerIds());
        if (!developerIds.contains(developer.id())) developerIds.add(developer.id());
        projectRepository.save(new Project(project.id(), project.organizationId(), project.name(), project.description(),
                project.productManagerId(), project.seniorManagerIds(), List.copyOf(developerIds), project.status(), project.createdAt(), Instant.now(), project.deadline()));
        Task assigned = tasks.save(new Task(task.id(), task.organizationId(), task.projectId(), task.title(), task.description(),
                task.createdBySeniorManagerId(), developer.id(), task.status(), task.createdAt(), Instant.now(), task.deadline()));
        if (!developer.id().equals(task.assignedDeveloperId())) {
            notifications.notifyUser(developer.id(), task.organizationId(), "TASK_ASSIGNED", "New task assigned",
                    "You were assigned the task: " + task.title(), "TASK", task.id());
        }
        return assigned;
    }

    public Task updateStatus(String userId, String taskId, TaskRequests.UpdateTaskStatusRequest request) {
        Task task = find(taskId);
        UserAccount developer = projects.requireRole(userId, RegistrationRole.DEVELOPER);
        if (!developer.id().equals(task.assignedDeveloperId())) throw new IllegalArgumentException("Only the assigned Developer can update this task");
        projects.requireView(developer, projects.find(task.projectId()));
        if (task.status() == TaskStatus.COMPLETED || task.status() == TaskStatus.CANCELLED) throw new IllegalArgumentException("Completed or cancelled tasks cannot be changed");
        if (request.status() != TaskStatus.IN_PROGRESS && request.status() != TaskStatus.CANCELLED) {
            throw new IllegalArgumentException("Developers can set task status to IN_PROGRESS or CANCELLED; submit work to mark it SUBMITTED");
        }
        return tasks.save(new Task(task.id(), task.organizationId(), task.projectId(), task.title(), task.description(),
                task.createdBySeniorManagerId(), task.assignedDeveloperId(), request.status(), task.createdAt(), Instant.now(), task.deadline()));
    }

    public Task markSubmitted(Task task) {
        return tasks.save(new Task(task.id(), task.organizationId(), task.projectId(), task.title(), task.description(),
                task.createdBySeniorManagerId(), task.assignedDeveloperId(), TaskStatus.SUBMITTED, task.createdAt(), Instant.now(), task.deadline()));
    }
    public Task requestChanges(Task task) {
        if (task.status() != TaskStatus.SUBMITTED) throw new IllegalArgumentException("Only a submitted task can be returned for changes");
        return tasks.save(new Task(task.id(), task.organizationId(), task.projectId(), task.title(), task.description(),
                task.createdBySeniorManagerId(), task.assignedDeveloperId(), TaskStatus.IN_PROGRESS, task.createdAt(), Instant.now(), task.deadline()));
    }
    public Task get(String userId, String taskId) {
        Task task = find(taskId);
        UserAccount user = projects.user(userId);
        projects.requireView(user, projects.find(task.projectId()));
        if (user.role() == RegistrationRole.DEVELOPER && !user.id().equals(task.assignedDeveloperId()))
            throw new IllegalArgumentException("Developers can only view their assigned tasks");
        if (user.role() == RegistrationRole.SENIOR_MANAGER && !user.id().equals(task.createdBySeniorManagerId()))
            throw new IllegalArgumentException("Senior Managers can only view tasks assigned to them");
        return task;
    }
    public Task markCompleted(Task task) {
        return tasks.save(new Task(task.id(), task.organizationId(), task.projectId(), task.title(), task.description(),
                task.createdBySeniorManagerId(), task.assignedDeveloperId(), TaskStatus.COMPLETED, task.createdAt(), Instant.now(), task.deadline()));
    }
    public Task assignForRework(Task task, String developerId) {
        if (task.status() == TaskStatus.COMPLETED || task.status() == TaskStatus.CANCELLED) throw new IllegalArgumentException("Completed or cancelled tasks cannot be reopened");
        List<String> developerIds = new java.util.ArrayList<>();
        Project project = projects.find(task.projectId());
        developerIds.addAll(project.developerIds());
        if (!developerIds.contains(developerId)) developerIds.add(developerId);
        projectRepository.save(new Project(project.id(), project.organizationId(), project.name(), project.description(),
                project.productManagerId(), project.seniorManagerIds(), List.copyOf(developerIds), project.status(), project.createdAt(), Instant.now(), project.deadline()));
        return tasks.save(new Task(task.id(), task.organizationId(), task.projectId(), task.title(), task.description(),
                task.createdBySeniorManagerId(), developerId, TaskStatus.IN_PROGRESS, task.createdAt(), Instant.now(), task.deadline()));
    }
    public void addDeveloperToProject(Project project, String developerId) {
        if (project.developerIds().contains(developerId)) return;
        List<String> developerIds = new java.util.ArrayList<>(project.developerIds());
        developerIds.add(developerId);
        projectRepository.save(new Project(project.id(), project.organizationId(), project.name(), project.description(),
                project.productManagerId(), project.seniorManagerIds(), List.copyOf(developerIds), project.status(), project.createdAt(), Instant.now(), project.deadline()));
    }
    public Task find(String id) { return tasks.findById(id).orElseThrow(() -> new NoSuchElementException("Task was not found")); }
    private String clean(String value) { return value == null ? "" : value.trim(); }
}
