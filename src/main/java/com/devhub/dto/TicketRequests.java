package com.devhub.dto;

import com.devhub.ticket.TicketPriority;
import com.devhub.ticket.TicketStatus;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class TicketRequests {
    private TicketRequests() { }
    public record CreateTicketRequest(@NotBlank @Size(max=180) String title, @NotBlank @Size(max=8000) String description,
                                      @NotNull TicketPriority priority, @NotBlank String seniorManagerId,
                                      @NotBlank @Size(max=1000) String codeLocation) { }
    public record UpdatePriorityRequest(@NotNull TicketPriority priority) { }
    public record UpdateStatusRequest(@NotNull TicketStatus status) { }
    public record AssignDeveloperRequest(@NotBlank String developerId) { }
}
