package com.dotlinea.soulecho.config;

import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MemoryChatAssistantConfig {

    @Bean
    public MessageWindowChatMemory chatMemory() {

        return MessageWindowChatMemory.withMaxMessages(20);

    }
}
