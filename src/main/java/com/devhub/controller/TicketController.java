package com.devhub.controller;

import com.devhub.ticket.Ticket;

import com.devhub.dto.TicketRequests;
import com.devhub.service.TicketService;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
public class TicketController {
    private final TicketService tickets;
    public TicketController(TicketService tickets) { this.tickets = tickets; }

    @PostMapping("/api/v1/projects/{projectId}/tickets")
    @ResponseStatus(HttpStatus.CREATED)
    public Ticket create(@AuthenticationPrincipal Jwt jwt, @PathVariable String projectId,
                         @Valid @RequestBody TicketRequests.CreateTicketRequest request) { return tickets.create(jwt.getSubject(), projectId, request); }
    @GetMapping("/api/v1/organizations/tickets")
    public List<Ticket> listOrganization(@AuthenticationPrincipal Jwt jwt) { return tickets.listOrganization(jwt.getSubject()); }
    @GetMapping("/api/v1/projects/{projectId}/tickets")
    public List<Ticket> listProject(@AuthenticationPrincipal Jwt jwt, @PathVariable String projectId) { return tickets.listProject(jwt.getSubject(), projectId); }
    @PatchMapping("/api/v1/tickets/{ticketId}/priority")
    public Ticket priority(@AuthenticationPrincipal Jwt jwt, @PathVariable String ticketId,
                           @Valid @RequestBody TicketRequests.UpdatePriorityRequest request) { return tickets.updatePriority(jwt.getSubject(), ticketId, request); }
    @PatchMapping("/api/v1/tickets/{ticketId}/status")
    public Ticket status(@AuthenticationPrincipal Jwt jwt, @PathVariable String ticketId,
                         @Valid @RequestBody TicketRequests.UpdateStatusRequest request) { return tickets.updateStatus(jwt.getSubject(), ticketId, request); }
    @PutMapping("/api/v1/tickets/{ticketId}/assignee")
    public Ticket assign(@AuthenticationPrincipal Jwt jwt, @PathVariable String ticketId,
                         @Valid @RequestBody TicketRequests.AssignDeveloperRequest request) { return tickets.assignDeveloper(jwt.getSubject(), ticketId, request); }
}
