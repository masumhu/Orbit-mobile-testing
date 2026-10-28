package com.devhub.service;

import com.devhub.chat.ChannelScope;
import com.devhub.chat.ChatChannel;
import com.devhub.chat.ChatMessage;
import com.devhub.organization.Organization;

import com.devhub.repository.ChatChannelRepository;
import com.devhub.repository.ChatMessageRepository;
import com.devhub.dto.ChatRequests;

import com.devhub.auth.RegistrationRole;
import com.devhub.auth.UserAccount;
import com.devhub.repository.UserAccountRepository;
import com.devhub.project.Project;
import com.devhub.service.ProjectService;
import org.springframework.stereotype.Service;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;

@Service
public class ChatService {
    private final ChatChannelRepository channels;
    private final ChatMessageRepository messages;
    private final UserAccountRepository users;
    private final ProjectService projects;

    public ChatService(ChatChannelRepository channels, ChatMessageRepository messages, UserAccountRepository users, ProjectService projects) {
        this.channels = channels; this.messages = messages; this.users = users; this.projects = projects;
    }

    public ChatChannel createOrganizationChannel(String actorId, String organizationId, ChatRequests.CreateChannelRequest request) {
        UserAccount actor = manager(actorId);
        if (!organizationId.equals(actor.organizationId())) throw new IllegalArgumentException("Organization does not match your account");
        List<String> memberIds = validateMembers(request.memberIds(), organizationId, null);
        return create(organizationId, null, ChannelScope.ORGANIZATION, request.name(), actorId, memberIds);
    }

    public ChatChannel createProjectChannel(String actorId, String projectId, ChatRequests.CreateChannelRequest request) {
        UserAccount actor = manager(actorId);
        Project project = projects.find(projectId);
        requireProjectManager(actor, project);
        List<String> memberIds = validateMembers(request.memberIds(), project.organizationId(), project);
        return create(project.organizationId(), projectId, ChannelScope.PROJECT, request.name(), actorId, memberIds);
    }

    public List<ChatChannel> organizationChannels(String actorId, String organizationId) {
        UserAccount actor = chatUser(actorId);
        if (!organizationId.equals(actor.organizationId())) throw new IllegalArgumentException("Organization does not match your account");
        return channels.findByOrganizationIdAndScope(organizationId, ChannelScope.ORGANIZATION).stream()
                .filter(channel -> channel.memberIds().contains(actorId)).toList();
    }

    public List<ChatChannel> projectChannels(String actorId, String projectId) {
        UserAccount actor = chatUser(actorId);
        Project project = projects.find(projectId);
        if (!project.organizationId().equals(actor.organizationId())) throw new IllegalArgumentException("Project is outside your organization");
        return channels.findByProjectId(projectId).stream().filter(channel -> channel.memberIds().contains(actorId)).toList();
    }

    public ChatChannel addMember(String actorId, String channelId, String userId) {
        ChatChannel channel = findChannel(channelId);
        UserAccount actor = manager(actorId);
        requireManage(actor, channel);
        UserAccount user = requireEligibleMember(userId, channel.organizationId(), channel.projectId());
        List<String> memberIds = new ArrayList<>(channel.memberIds());
        if (!memberIds.contains(user.id())) memberIds.add(user.id());
        return saveMembers(channel, memberIds);
    }

    public ChatChannel removeMember(String actorId, String channelId, String userId) {
        ChatChannel channel = findChannel(channelId);
        UserAccount actor = manager(actorId);
        requireManage(actor, channel);
        if (!channel.memberIds().contains(userId)) return channel;
        List<String> memberIds = new ArrayList<>(channel.memberIds());
        memberIds.remove(userId);
        return saveMembers(channel, memberIds);
    }

    public List<ChatMessage> listMessages(String actorId, String channelId) {
        requireChannelMember(actorId, findChannel(channelId));
        return messages.findByChannelIdOrderByCreatedAtAsc(channelId).stream()
                .map(message -> message.deletedAt() == null ? message : new ChatMessage(message.id(), message.channelId(), message.authorUserId(),
                        null, message.parentMessageId(), message.createdAt(), message.updatedAt(), message.deletedAt())).toList();
    }

    public void authorizeChannel(String actorId, String channelId) {
        requireChannelMember(actorId, findChannel(channelId));
    }

    public ChatMessage send(String actorId, String channelId, ChatRequests.CreateMessageRequest request) {
        ChatChannel channel = findChannel(channelId);
        requireChannelMember(actorId, channel);
        if (request.parentMessageId() != null) {
            ChatMessage parent = messages.findById(request.parentMessageId()).orElseThrow(() -> new NoSuchElementException("Reply target was not found"));
            if (!channelId.equals(parent.channelId()) || parent.parentMessageId() != null) throw new IllegalArgumentException("Replies must point to a top-level message in this channel");
        }
        Instant now = Instant.now();
        return messages.save(new ChatMessage(null, channelId, actorId, request.body().trim(), request.parentMessageId(), now, now, null));
    }

    public ChatMessage updateMessage(String actorId, String messageId, ChatRequests.UpdateMessageRequest request) {
        ChatMessage message = findMessage(messageId);
        requireChannelMember(actorId, findChannel(message.channelId()));
        if (!actorId.equals(message.authorUserId()) || message.deletedAt() != null) throw new IllegalArgumentException("Only the author can edit an active message");
        return messages.save(new ChatMessage(message.id(), message.channelId(), actorId, request.body().trim(), message.parentMessageId(),
                message.createdAt(), Instant.now(), null));
    }

    public void deleteMessage(String actorId, String messageId) {
        ChatMessage message = findMessage(messageId);
        requireChannelMember(actorId, findChannel(message.channelId()));
        if (!actorId.equals(message.authorUserId())) throw new IllegalArgumentException("Only the author can delete this message");
        messages.save(new ChatMessage(message.id(), message.channelId(), actorId, null, message.parentMessageId(),
                message.createdAt(), message.updatedAt(), Instant.now()));
    }

    private ChatChannel create(String organizationId, String projectId, ChannelScope scope, String name, String actorId, List<String> requestedMembers) {
        List<String> members = new ArrayList<>(requestedMembers);
        if (!members.contains(actorId)) members.add(actorId);
        Instant now = Instant.now();
        return channels.save(new ChatChannel(null, organizationId, projectId, scope, name.trim(), actorId, List.copyOf(members), now, now));
    }
    private ChatChannel saveMembers(ChatChannel channel, List<String> memberIds) {
        return channels.save(new ChatChannel(channel.id(), channel.organizationId(), channel.projectId(), channel.scope(), channel.name(),
                channel.createdByUserId(), List.copyOf(memberIds), channel.createdAt(), Instant.now()));
    }
    private ChatChannel findChannel(String id) { return channels.findById(id).orElseThrow(() -> new NoSuchElementException("Chat channel was not found")); }
    private ChatMessage findMessage(String id) { return messages.findById(id).orElseThrow(() -> new NoSuchElementException("Chat message was not found")); }
    private UserAccount chatUser(String id) {
        UserAccount user = users.findById(id).orElseThrow(() -> new NoSuchElementException("User was not found"));
        if (user.role() == RegistrationRole.OWNER) throw new IllegalArgumentException("Organization owners do not have chat access");
        return user;
    }
    private UserAccount manager(String id) {
        UserAccount user = chatUser(id);
        if (user.role() != RegistrationRole.PRODUCT_MANAGER && user.role() != RegistrationRole.SENIOR_MANAGER)
            throw new IllegalArgumentException("Only Product Managers and Senior Managers can manage channels");
        return user;
    }
    private void requireChannelMember(String userId, ChatChannel channel) {
        UserAccount user = chatUser(userId);
        if (!channel.memberIds().contains(userId) || !channel.organizationId().equals(user.organizationId()))
            throw new IllegalArgumentException("You do not have access to this channel");
        if (channel.scope() == ChannelScope.PROJECT) {
            Project project = projects.find(channel.projectId());
            if (!projectMember(user, project)) throw new IllegalArgumentException("You are no longer a member of this project");
        }
    }
    private void requireManage(UserAccount actor, ChatChannel channel) {
        if (!channel.organizationId().equals(actor.organizationId())) throw new IllegalArgumentException("Channel is outside your organization");
        if (channel.scope() == ChannelScope.PROJECT) requireProjectManager(actor, projects.find(channel.projectId()));
    }
    private void requireProjectManager(UserAccount actor, Project project) {
        boolean allowed = actor.role() == RegistrationRole.PRODUCT_MANAGER && project.productManagerId().equals(actor.id())
                || actor.role() == RegistrationRole.SENIOR_MANAGER && project.seniorManagerIds().contains(actor.id());
        if (!allowed || !project.organizationId().equals(actor.organizationId())) throw new IllegalArgumentException("Only an assigned Product Manager or Senior Manager can manage this project channel");
    }
    private List<String> validateMembers(List<String> ids, String organizationId, Project project) {
        if (ids == null) return List.of();
        if (ids.size() != ids.stream().distinct().count()) throw new IllegalArgumentException("Channel member IDs must be unique");
        for (String id : ids) requireEligibleMember(id, organizationId, project == null ? null : project.id());
        return List.copyOf(ids);
    }
    private UserAccount requireEligibleMember(String userId, String organizationId, String projectId) {
        UserAccount user = chatUser(userId);
        if (!organizationId.equals(user.organizationId())) throw new IllegalArgumentException("Channel members must belong to the same organization");
        if (projectId != null && !projectMember(user, projects.find(projectId))) throw new IllegalArgumentException("Channel members must have access to the project");
        return user;
    }
    private boolean projectMember(UserAccount user, Project project) {
        return user.role() == RegistrationRole.PRODUCT_MANAGER && project.productManagerId().equals(user.id())
                || user.role() == RegistrationRole.SENIOR_MANAGER && project.seniorManagerIds().contains(user.id())
                || user.role() == RegistrationRole.DEVELOPER && project.developerIds().contains(user.id());
    }
}
