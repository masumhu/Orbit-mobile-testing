package com.devhub.service;

import com.devhub.project.Project;
import com.devhub.project.ProjectStatus;

import com.devhub.repository.ProjectRepository;
import com.devhub.dto.ProjectRequests;

import com.devhub.auth.RegistrationRole;
import com.devhub.auth.UserAccount;
import com.devhub.repository.UserAccountRepository;
import com.devhub.repository.TaskRepository;
import com.devhub.task.TaskStatus;
import com.devhub.repository.TicketRepository;
import com.devhub.ticket.TicketStatus;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;

@Service
public class ProjectService {
    private final ProjectRepository projects;
    private final UserAccountRepository users;
    private final TaskRepository tasks;
    private final TicketRepository tickets;

    public ProjectService(ProjectRepository projects, UserAccountRepository users, TaskRepository tasks, TicketRepository tickets) {
        this.projects = projects;
        this.users = users;
        this.tasks = tasks;
        this.tickets = tickets;
    }

    public Project create(String userId, ProjectRequests.CreateProjectRequest request) {
        UserAccount pm = requireRole(userId, RegistrationRole.PRODUCT_MANAGER);
        List<String> seniors = validateSeniorIds(pm.organizationId(), request.seniorManagerIds());
        Instant now = Instant.now();
        return projects.save(new Project(null, pm.organizationId(), request.name().trim(), clean(request.description()),
                pm.id(), seniors, List.of(), ProjectStatus.ACTIVE, now, now, request.deadline()));
    }

    public List<Project> list(String userId) {
        UserAccount user = user(userId);
        List<Project> visible = switch (user.role()) {
            case OWNER -> projects.findByOrganizationId(user.organizationId());
            case PRODUCT_MANAGER -> projects.findByOrganizationIdAndProductManagerId(user.organizationId(), user.id());
            case SENIOR_MANAGER -> projects.findByOrganizationIdAndSeniorManagerIdsContaining(user.organizationId(), user.id());
            case DEVELOPER -> projects.findByOrganizationId(user.organizationId()).stream()
                    .filter(project -> project.developerIds().contains(user.id())).toList();
        };
        return visible.stream().sorted(java.util.Comparator
                .comparing(Project::deadline, java.util.Comparator.nullsLast(java.util.Comparator.naturalOrder()))
                .thenComparing(Project::createdAt, java.util.Comparator.nullsLast(java.util.Comparator.naturalOrder())))
                .toList();
    }

    public Project get(String userId, String projectId) {
        Project project = find(projectId);
        requireView(user(userId), project);
        return project;
    }

    public Project update(String userId, String projectId, ProjectRequests.UpdateProjectRequest request) {
        UserAccount pm = requireRole(userId, RegistrationRole.PRODUCT_MANAGER);
        Project project = find(projectId);
        if (!project.organizationId().equals(pm.organizationId()) || !project.productManagerId().equals(pm.id())) {
            throw new IllegalArgumentException("Only the project's Product Manager can update it");
        }
        List<String> seniors = validateSeniorIds(project.organizationId(), request.seniorManagerIds());
        Project updated = new Project(project.id(), project.organizationId(), request.name().trim(), clean(request.description()),
                project.productManagerId(), seniors, project.developerIds(), request.status() == null ? project.status() : request.status(),
                project.createdAt(), Instant.now(), request.deadline() == null ? project.deadline() : request.deadline());
        return projects.save(updated);
    }

    public void leave(String userId, String projectId) {
        UserAccount user = user(userId);
        Project project = find(projectId);
        requireView(user, project);
        if (user.role() != RegistrationRole.DEVELOPER && user.role() != RegistrationRole.SENIOR_MANAGER) {
            throw new IllegalArgumentException("Only Developers and Senior Managers can leave a project");
        }
        boolean activeTasks = tasks.findByProjectId(projectId).stream().anyMatch(task ->
                (user.id().equals(task.assignedDeveloperId()) || user.id().equals(task.createdBySeniorManagerId()))
                        && task.status() != TaskStatus.COMPLETED && task.status() != TaskStatus.CANCELLED);
        boolean activeTickets = tickets.findByProjectId(projectId).stream().anyMatch(ticket ->
                (user.id().equals(ticket.assignedDeveloperId()) || user.id().equals(ticket.assignedSeniorManagerId()))
                        && ticket.status() != TicketStatus.CLOSED);
        if (activeTasks || activeTickets) throw new IllegalStateException("Reassign or close your active work before leaving this project");
        if (user.role() == RegistrationRole.DEVELOPER) {
            List<String> developerIds = project.developerIds().stream().filter(id -> !id.equals(user.id())).toList();
            projects.save(new Project(project.id(), project.organizationId(), project.name(), project.description(), project.productManagerId(),
                    project.seniorManagerIds(), developerIds, project.status(), project.createdAt(), Instant.now(), project.deadline()));
        } else {
            List<String> seniorIds = project.seniorManagerIds().stream().filter(id -> !id.equals(user.id())).toList();
            projects.save(new Project(project.id(), project.organizationId(), project.name(), project.description(), project.productManagerId(),
                    seniorIds, project.developerIds(), project.status(), project.createdAt(), Instant.now(), project.deadline()));
        }
    }

    public Project find(String id) { return projects.findById(id).orElseThrow(() -> new NoSuchElementException("Project was not found")); }

    public UserAccount requireView(UserAccount user, Project project) {
        if (!project.organizationId().equals(user.organizationId())) throw new IllegalArgumentException("Project is outside your organization");
        boolean allowed = switch (user.role()) {
            case OWNER -> true;
            case PRODUCT_MANAGER -> project.productManagerId().equals(user.id());
            case SENIOR_MANAGER -> project.seniorManagerIds().contains(user.id());
            case DEVELOPER -> project.developerIds().contains(user.id());
        };
        if (!allowed) throw new IllegalArgumentException("You do not have access to this project");
        return user;
    }

    private List<String> validateSeniorIds(String organizationId, List<String> ids) {
        if (ids == null) return List.of();
        if (ids.size() != ids.stream().distinct().count()) throw new IllegalArgumentException("Senior Manager IDs must be unique");
        for (String id : ids) {
            UserAccount senior = user(id);
            if (senior.role() != RegistrationRole.SENIOR_MANAGER || !organizationId.equals(senior.organizationId())) {
                throw new IllegalArgumentException("Each assigned Senior Manager must belong to this organization");
            }
        }
        return List.copyOf(ids);
    }

    public UserAccount requireRole(String id, RegistrationRole role) {
        UserAccount user = user(id);
        if (user.role() != role) throw new IllegalArgumentException("This action requires the " + role + " role");
        return user;
    }

    public UserAccount user(String id) { return users.findById(id).orElseThrow(() -> new NoSuchElementException("User was not found")); }
    private String clean(String value) { return value == null ? "" : value.trim(); }
}
