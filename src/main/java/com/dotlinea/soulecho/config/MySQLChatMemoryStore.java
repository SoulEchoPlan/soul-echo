package com.dotlinea.soulecho.config;

import com.dotlinea.soulecho.entity.ChatMemory;
import com.dotlinea.soulecho.mapper.ChatMemoryMapper;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ChatMessageDeserializer;
import dev.langchain4j.data.message.ChatMessageSerializer;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class MySQLChatMemoryStore implements ChatMemoryStore {

    @Autowired
    private ChatMemoryMapper chatMemoryMapper;

    @Override
    public List<ChatMessage> getMessages(Object memoryId) {
        ChatMemory messages = chatMemoryMapper.getMessagesId(memoryId);
        if (messages == null) {
            return new ArrayList<ChatMessage>();
        }
        List<ChatMessage> chatMessages = ChatMessageDeserializer.messagesFromJson(messages.getMessagesJson());
        return chatMessages;
    }

    @Override
    public void updateMessages(Object memoryId, List<ChatMessage> messages) {
        String messagesToJson = ChatMessageSerializer.messagesToJson(messages);
        ChatMemory chatMemory = chatMemoryMapper.getMessagesId(memoryId);
        if (chatMemory==null){
            ChatMemory chatMemory1 = new ChatMemory();
            chatMemory1.setMessagesJson(messagesToJson);
            chatMemory1.setMemoryId(String.valueOf(memoryId));
            chatMemoryMapper.insert(chatMemory1);
            return;
        }
        chatMemoryMapper.updateMessagesId(memoryId,messagesToJson);
    }

    @Override
    public void deleteMessages(Object memoryId) {
        chatMemoryMapper.deleteMessagesId(memoryId);
    }
}