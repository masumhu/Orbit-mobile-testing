package com.devhub.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

public final class ChatRequests {
    private ChatRequests() { }
    public record CreateChannelRequest(@NotBlank @Size(max=100) String name, List<String> memberIds) { }
    public record AddMemberRequest(@NotBlank String userId) { }
    public record CreateMessageRequest(@NotBlank @Size(max=10000) String body, String parentMessageId) { }
    public record UpdateMessageRequest(@NotBlank @Size(max=10000) String body) { }
}
