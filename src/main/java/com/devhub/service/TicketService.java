package com.devhub.service;

import com.devhub.ticket.Ticket;
import com.devhub.ticket.TicketChange;
import com.devhub.ticket.TicketPriority;
import com.devhub.ticket.TicketStatus;

import com.devhub.repository.TicketRepository;
import com.devhub.dto.TicketRequests;

import com.devhub.auth.RegistrationRole;
import com.devhub.auth.UserAccount;
import com.devhub.project.Project;
import com.devhub.service.ProjectService;
import com.devhub.repository.CodeSubmissionRepository;
import com.devhub.task.Task;
import com.devhub.service.TaskService;
import org.springframework.stereotype.Service;
import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;

@Service
public class TicketService {
    private final TicketRepository tickets;
    private final ProjectService projects;
    private final TaskService tasks;
    private final CodeSubmissionRepository submissions;
    private final UserNotificationService notifications;

    public TicketService(TicketRepository tickets, ProjectService projects, TaskService tasks,
                         CodeSubmissionRepository submissions, UserNotificationService notifications) {
        this.tickets = tickets; this.projects = projects; this.tasks = tasks; this.submissions = submissions;
        this.notifications = notifications;
    }

    public Ticket create(String userId, String projectId, TicketRequests.CreateTicketRequest request) {
        UserAccount pm = projects.requireRole(userId, RegistrationRole.PRODUCT_MANAGER);
        Project project = projects.find(projectId);
        projects.requireView(pm, project);
        UserAccount senior = validateSenior(project, request.seniorManagerId());
        Instant now = Instant.now();
        Ticket created = tickets.save(new Ticket(null, project.organizationId(), project.id(), null, request.title().trim(), request.description().trim(),
                request.priority(), TicketStatus.OPEN, request.codeLocation().trim(), pm.id(), senior.id(), null,
                List.of(new TicketChange("created", null, TicketStatus.OPEN.name(), pm.id(), now)), now, now));
        notifications.notifyUser(senior.id(), project.organizationId(), "TICKET_ASSIGNED", "New ticket assigned",
                "A ticket was assigned to you: " + created.title(), "TICKET", created.id());
        return created;
    }

    public List<Ticket> listProject(String userId, String projectId) {
        Project project = projects.find(projectId);
        UserAccount user = projects.user(userId);
        projects.requireView(user, project);
        return filterForRole(tickets.findByProjectId(projectId), user);
    }

    public List<Ticket> listOrganization(String userId) {
        UserAccount user = projects.user(userId);
        List<String> visibleProjectIds = projects.list(userId).stream().map(Project::id).toList();
        return tickets.findByOrganizationId(user.organizationId()).stream().filter(ticket -> visibleProjectIds.contains(ticket.projectId()))
                .filter(ticket -> user.role() != RegistrationRole.DEVELOPER || user.id().equals(ticket.assignedDeveloperId()))
                .filter(ticket -> user.role() != RegistrationRole.SENIOR_MANAGER || user.id().equals(ticket.assignedSeniorManagerId())).toList();
    }

    public Ticket updatePriority(String userId, String ticketId, TicketRequests.UpdatePriorityRequest request) {
        UserAccount pm = projects.requireRole(userId, RegistrationRole.PRODUCT_MANAGER);
        Ticket ticket = find(ticketId);
        projects.requireView(pm, projects.find(ticket.projectId()));
        if (!ticket.createdByProductManagerId().equals(pm.id())) throw new IllegalArgumentException("Only the ticket's Product Manager can set its priority");
        return saveChange(ticket, "priority", ticket.priority().name(), request.priority().name(), pm.id(),
                ticket.assignedSeniorManagerId(), ticket.assignedDeveloperId(), request.priority(), ticket.status());
    }

    public Ticket updateStatus(String userId, String ticketId, TicketRequests.UpdateStatusRequest request) {
        UserAccount senior = projects.requireRole(userId, RegistrationRole.SENIOR_MANAGER);
        Ticket ticket = find(ticketId);
        projects.requireView(senior, projects.find(ticket.projectId()));
        if (!ticket.assignedSeniorManagerId().equals(senior.id())) throw new IllegalArgumentException("Only the assigned Senior Manager can update ticket status");
        return saveChange(ticket, "status", ticket.status().name(), request.status().name(), senior.id(),
                ticket.assignedSeniorManagerId(), ticket.assignedDeveloperId(), ticket.priority(), request.status());
    }

    public Ticket assignDeveloper(String userId, String ticketId, TicketRequests.AssignDeveloperRequest request) {
        UserAccount senior = projects.requireRole(userId, RegistrationRole.SENIOR_MANAGER);
        Ticket ticket = find(ticketId);
        projects.requireView(senior, projects.find(ticket.projectId()));
        if (!ticket.assignedSeniorManagerId().equals(senior.id())) throw new IllegalArgumentException("Only the assigned Senior Manager can assign this ticket");
        Project project = projects.find(ticket.projectId());
        UserAccount developer = projects.user(request.developerId());
        if (developer.role() != RegistrationRole.DEVELOPER || !project.organizationId().equals(developer.organizationId())) {
            throw new IllegalArgumentException("Developer must belong to this organization");
        }
        tasks.addDeveloperToProject(project, developer.id());
        if (ticket.submissionId() != null) {
            var submission = submissions.findById(ticket.submissionId()).orElseThrow(() -> new NoSuchElementException("Related submission was not found"));
            Task task = tasks.find(submission.taskId());
            if (!task.createdBySeniorManagerId().equals(senior.id())) throw new IllegalArgumentException("Ticket does not belong to one of your tasks");
            tasks.assignForRework(task, developer.id());
        }
        List<TicketChange> changes = new java.util.ArrayList<>(ticket.history());
        Instant now = Instant.now();
        changes.add(new TicketChange("assignee", ticket.assignedDeveloperId(), developer.id(), senior.id(), now));
        TicketStatus nextStatus = ticket.status();
        if (ticket.status() == TicketStatus.OPEN) {
            changes.add(new TicketChange("status", ticket.status().name(), TicketStatus.IN_PROCESS.name(), senior.id(), now));
            nextStatus = TicketStatus.IN_PROCESS;
        }
        Ticket assigned = tickets.save(new Ticket(ticket.id(), ticket.organizationId(), ticket.projectId(), ticket.submissionId(), ticket.title(),
                ticket.description(), ticket.priority(), nextStatus, ticket.codeLocation(), ticket.createdByProductManagerId(),
                ticket.assignedSeniorManagerId(), developer.id(), List.copyOf(changes), ticket.createdAt(), now));
        if (!developer.id().equals(ticket.assignedDeveloperId())) {
            notifications.notifyUser(developer.id(), ticket.organizationId(), "TICKET_ASSIGNED", "Ticket assigned to you",
                    "You were assigned the ticket: " + ticket.title(), "TICKET", ticket.id());
        }
        return assigned;
    }

    public Ticket createFromSubmission(String productManagerId, Project project, String submissionId,
                                       String seniorManagerId, String title, String description, TicketPriority priority, String codeLocation) {
        UserAccount pm = projects.requireRole(productManagerId, RegistrationRole.PRODUCT_MANAGER);
        projects.requireView(pm, project);
        UserAccount senior = validateSenior(project, seniorManagerId);
        Instant now = Instant.now();
        Ticket created = tickets.save(new Ticket(null, project.organizationId(), project.id(), submissionId, title.trim(), description.trim(),
                priority, TicketStatus.OPEN, codeLocation, pm.id(), senior.id(), null,
                List.of(new TicketChange("created", null, TicketStatus.OPEN.name(), pm.id(), now)), now, now));
        notifications.notifyUser(senior.id(), project.organizationId(), "TICKET_ASSIGNED", "New ticket assigned",
                "A ticket was created from a reviewed submission and assigned to you: " + created.title(), "TICKET", created.id());
        return created;
    }

    public Ticket find(String id) { return tickets.findById(id).orElseThrow(() -> new NoSuchElementException("Ticket was not found")); }

    private Ticket saveChange(Ticket ticket, String field, String oldValue, String newValue, String actor,
                              String seniorId, String developerId, TicketPriority priority, TicketStatus status) {
        List<TicketChange> changes = new java.util.ArrayList<>(ticket.history());
        changes.add(new TicketChange(field, oldValue, newValue, actor, Instant.now()));
        return tickets.save(new Ticket(ticket.id(), ticket.organizationId(), ticket.projectId(), ticket.submissionId(),
                ticket.title(), ticket.description(), priority, status, ticket.codeLocation(), ticket.createdByProductManagerId(),
                seniorId, developerId, List.copyOf(changes), ticket.createdAt(), Instant.now()));
    }

    private UserAccount validateSenior(Project project, String seniorId) {
        UserAccount senior = projects.user(seniorId);
        if (senior.role() != RegistrationRole.SENIOR_MANAGER || !project.organizationId().equals(senior.organizationId())
                || !project.seniorManagerIds().contains(senior.id())) throw new IllegalArgumentException("Senior Manager must be assigned to this project");
        return senior;
    }

    private List<Ticket> filterForRole(List<Ticket> tickets, UserAccount user) {
        return switch (user.role()) {
            case OWNER, PRODUCT_MANAGER -> tickets;
            case SENIOR_MANAGER -> tickets.stream().filter(t -> t.assignedSeniorManagerId().equals(user.id())).toList();
            case DEVELOPER -> tickets.stream().filter(t -> user.id().equals(t.assignedDeveloperId())).toList();
        };
    }
}
