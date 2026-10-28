package com.devhub.controller;

import com.devhub.chat.ChatChannel;
import com.devhub.chat.ChatMessage;

import com.devhub.dto.ChatRequests;
import com.devhub.service.ChatService;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
public class ChatController {
    private final ChatService chat;
    public ChatController(ChatService chat) { this.chat = chat; }

    @PostMapping("/api/v1/organizations/{organizationId}/channels")
    @ResponseStatus(HttpStatus.CREATED)
    public ChatChannel createOrganization(@AuthenticationPrincipal Jwt jwt, @PathVariable String organizationId,
                                          @RequestBody @Valid ChatRequests.CreateChannelRequest request) {
        return chat.createOrganizationChannel(jwt.getSubject(), organizationId, request);
    }
    @GetMapping("/api/v1/organizations/{organizationId}/channels")
    public List<ChatChannel> organizationChannels(@AuthenticationPrincipal Jwt jwt, @PathVariable String organizationId) {
        return chat.organizationChannels(jwt.getSubject(), organizationId);
    }
    @PostMapping("/api/v1/projects/{projectId}/channels")
    @ResponseStatus(HttpStatus.CREATED)
    public ChatChannel createProject(@AuthenticationPrincipal Jwt jwt, @PathVariable String projectId,
                                     @RequestBody @Valid ChatRequests.CreateChannelRequest request) { return chat.createProjectChannel(jwt.getSubject(), projectId, request); }
    @GetMapping("/api/v1/projects/{projectId}/channels")
    public List<ChatChannel> projectChannels(@AuthenticationPrincipal Jwt jwt, @PathVariable String projectId) { return chat.projectChannels(jwt.getSubject(), projectId); }
    @PostMapping("/api/v1/channels/{channelId}/members")
    public ChatChannel addMember(@AuthenticationPrincipal Jwt jwt, @PathVariable String channelId,
                                 @RequestBody @Valid ChatRequests.AddMemberRequest request) { return chat.addMember(jwt.getSubject(), channelId, request.userId()); }
    @DeleteMapping("/api/v1/channels/{channelId}/members/{userId}")
    public ChatChannel removeMember(@AuthenticationPrincipal Jwt jwt, @PathVariable String channelId, @PathVariable String userId) { return chat.removeMember(jwt.getSubject(), channelId, userId); }
    @GetMapping("/api/v1/channels/{channelId}/messages")
    public List<ChatMessage> messages(@AuthenticationPrincipal Jwt jwt, @PathVariable String channelId) { return chat.listMessages(jwt.getSubject(), channelId); }
    @PostMapping("/api/v1/channels/{channelId}/messages")
    @ResponseStatus(HttpStatus.CREATED)
    public ChatMessage send(@AuthenticationPrincipal Jwt jwt, @PathVariable String channelId,
                            @RequestBody @Valid ChatRequests.CreateMessageRequest request) { return chat.send(jwt.getSubject(), channelId, request); }
    @PatchMapping("/api/v1/messages/{messageId}")
    public ChatMessage update(@AuthenticationPrincipal Jwt jwt, @PathVariable String messageId,
                              @RequestBody @Valid ChatRequests.UpdateMessageRequest request) { return chat.updateMessage(jwt.getSubject(), messageId, request); }
    @DeleteMapping("/api/v1/messages/{messageId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal Jwt jwt, @PathVariable String messageId) { chat.deleteMessage(jwt.getSubject(), messageId); }
}
