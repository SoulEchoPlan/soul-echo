package com.dotlinea.soulecho.assistant;

import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import dev.langchain4j.service.spring.AiService;
import reactor.core.publisher.Flux;

import static dev.langchain4j.service.spring.AiServiceWiringMode.EXPLICIT;

@AiService(
        wiringMode =EXPLICIT,
//        chatModel = "qwenChatModel",
        streamingChatModel = "qwenStreamingChatModel",
        chatMemoryProvider = "chatMemoryProvider")
public interface SeparateChatAssistant {

    @SystemMessage("你是{{prompt}},请在用户发起第一次会话的时候，和用户打招呼，并介绍你是谁。今天是{{current_date}}.")
    Flux<String> chat(@MemoryId Long id, @UserMessage String userMessage,@V("prompt") String prompt);
}
