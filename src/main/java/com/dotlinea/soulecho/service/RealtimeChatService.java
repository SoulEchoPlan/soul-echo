package com.dotlinea.soulecho.service;

import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.WebSocketSession;

import java.util.function.Consumer;

/**
 * 实时聊天服务接口
 * <p>
 * 处理WebSocket连接中的音频消息和文本回复
 * </p>
 *
 * @author fanfan187
 * @version v1.0.0
 * @since v1.0.0
 */
public interface RealtimeChatService {

    /**
     * 处理音频消息
     * @param session WebSocket会话
     * @param message 音频消息
     */
    void handleBinaryMessage(WebSocketSession session, BinaryMessage message);

    /**
     * 处理流式文本聊天
     * @param personaPrompt 角色设定
     * @param userInput 用户输入
     * @param sessionId 会话ID
     * @param chunkConsumer 文本块消费者，每收到一块文本就会被调用
     */
    void processTextChatStream(String personaPrompt, String userInput, String sessionId, Consumer<String> chunkConsumer);

    /**
     * 处理流式文本聊天（支持知识库增强）
     * @param personaPrompt 角色设定
     * @param userInput 用户输入
     * @param sessionId 会话ID
     * @param characterName 角色名称（用于知识库检索）
     * @param chunkConsumer 文本块消费者，每收到一块文本就会被调用
     */
    void processTextChatStream(String personaPrompt, String userInput, String sessionId, String characterName, Consumer<String> chunkConsumer);

    /**
     * 清理会话
     * @param sessionId 会话ID
     */
    void cleanupSession(String sessionId);
}