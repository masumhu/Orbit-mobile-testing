package com.devhub.repository;

import com.devhub.chat.ChatMessage;

import org.springframework.data.mongodb.repository.MongoRepository;
import java.util.List;

public interface ChatMessageRepository extends MongoRepository<ChatMessage, String> {
    List<ChatMessage> findByChannelIdOrderByCreatedAtAsc(String channelId);
    List<ChatMessage> findByChannelIdAndParentMessageIdOrderByCreatedAtAsc(String channelId, String parentMessageId);
}
