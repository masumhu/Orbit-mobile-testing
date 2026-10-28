package com.devhub.ticket;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.Instant;
import java.util.List;

@Document("tickets")
public record Ticket(@Id String id, String organizationId, String projectId, String submissionId,
                     String title, String description, TicketPriority priority, TicketStatus status,
                     String codeLocation, String createdByProductManagerId, String assignedSeniorManagerId,
                     String assignedDeveloperId, List<TicketChange> history, Instant createdAt, Instant updatedAt) { }
