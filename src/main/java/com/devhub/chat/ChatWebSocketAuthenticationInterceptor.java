package com.devhub.chat;

import com.devhub.service.ChatService;

import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class ChatWebSocketAuthenticationInterceptor implements ChannelInterceptor {
    private static final Pattern CHANNEL_DESTINATION = Pattern.compile("^/(?:topic/channels/([^/]+)|app/channels/([^/]+)/messages)$");
    private final JwtDecoder jwtDecoder;
    private final ChatService chat;

    public ChatWebSocketAuthenticationInterceptor(JwtDecoder jwtDecoder, ChatService chat) {
        this.jwtDecoder = jwtDecoder; this.chat = chat;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || accessor.getCommand() == null) return message;
        if (accessor.getCommand() == StompCommand.CONNECT) {
            List<String> headers = accessor.getNativeHeader("Authorization");
            if (headers == null || headers.isEmpty() || !headers.get(0).startsWith("Bearer ")) throw new IllegalArgumentException("Bearer access token is required");
            Jwt jwt = jwtDecoder.decode(headers.get(0).substring(7));
            accessor.setUser(new JwtAuthenticationToken(jwt));
            return message;
        }
        if (accessor.getCommand() == StompCommand.SUBSCRIBE || accessor.getCommand() == StompCommand.SEND) {
            if (!(accessor.getUser() instanceof JwtAuthenticationToken authentication)) throw new IllegalArgumentException("Authenticated WebSocket session is required");
            Matcher matcher = CHANNEL_DESTINATION.matcher(accessor.getDestination() == null ? "" : accessor.getDestination());
            if (!matcher.matches()) throw new IllegalArgumentException("Unsupported WebSocket destination");
            String channelId = matcher.group(1) != null ? matcher.group(1) : matcher.group(2);
            chat.authorizeChannel(authentication.getToken().getSubject(), channelId);
        }
        return message;
    }
}
