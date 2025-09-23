package com.dotlinea.soulecho.assistant;

import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.spring.AiService;

import static dev.langchain4j.service.spring.AiServiceWiringMode.EXPLICIT;

@AiService(
        wiringMode =EXPLICIT,
        chatModel = "qwenChatModel",
        chatMemoryProvider = "chatMemoryProvider")
public interface SeparateChatAssistant {
    @SystemMessage(fromResource = "libai.txt")
    String chat(@MemoryId Long id, @UserMessage String userMessage);

//    @SystemMessage(fromResource = "zhugeliang.txt")
//    String zhugeliangchat(@MemoryId Long id, @UserMessage String userMessage);
}
