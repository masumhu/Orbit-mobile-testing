package com.devhub.dto;

import com.devhub.ticket.TicketPriority;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class SubmissionRequests {
    private SubmissionRequests() { }
    public record CreateSubmissionRequest(String repositoryUrl, String branch, String commitSha,
                                         String pullRequestUrl, String storageReference) { }
    public record ReviewSubmissionRequest(@NotNull ReviewDecision decision, @Size(max=4000) String note,
                                         @Size(max=180) String ticketTitle, @Size(max=8000) String ticketDescription,
                                         TicketPriority priority, @Size(max=1000) String codeLocation) { }
    public record RequestChangesRequest(@NotBlank @Size(max=4000) String note) { }
    public enum ReviewDecision { ACCEPT, REQUEST_CHANGES }
}
