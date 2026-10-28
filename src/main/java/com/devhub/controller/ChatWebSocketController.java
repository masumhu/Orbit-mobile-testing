package com.devhub.controller;

import com.devhub.chat.ChatMessage;

import com.devhub.dto.ChatRequests;
import com.devhub.service.ChatService;

import com.devhub.dto.ChatRequests.CreateMessageRequest;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Controller;
import org.springframework.messaging.handler.annotation.Payload;

@Controller
public class ChatWebSocketController {
    private final ChatService chat;
    private final SimpMessagingTemplate messaging;

    public ChatWebSocketController(ChatService chat, SimpMessagingTemplate messaging) { this.chat = chat; this.messaging = messaging; }

    @MessageMapping("/channels/{channelId}/messages")
    public void send(@DestinationVariable String channelId, @Payload CreateMessageRequest request, Authentication authentication) {
        if (!(authentication instanceof JwtAuthenticationToken jwt)) throw new IllegalArgumentException("Authenticated WebSocket session is required");
        ChatMessage saved = chat.send(jwt.getToken().getSubject(), channelId, request);
        messaging.convertAndSend("/topic/channels/" + channelId, saved);
    }
}
