package com.dotlinea.soulecho.controller;

import com.dotlinea.soulecho.dto.WebSocketMessageDTO;
import com.dotlinea.soulecho.factory.WebSocketMessageFactory;
import com.dotlinea.soulecho.service.RealtimeChatService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.AbstractWebSocketHandler;

import java.net.URI;
import java.nio.charset.StandardCharsets;

/**
 * WebSocket 处理器
 * <p>
 * 负责处理WebSocket连接的生命周期和消息路由，支持文本和二进制消息处理
 * </p>
 *
 * @author fanfan187
 * @version v1.0.0
 * @since v1.0.0
 */
@Component
@AllArgsConstructor
public class ChatWebSocketHandler extends AbstractWebSocketHandler {

    private static final Logger logger = LoggerFactory.getLogger(ChatWebSocketHandler.class);
    private static final String PERSONA_PROMPT_PARAM = "personaPrompt";
    private static final String CHARACTER_ID_PARAM = "characterId";
    private static final String DEFAULT_PERSONA_PROMPT = "你是一个友好、有帮助的AI助手。请用自然、亲切的语气与用户对话。";

    private final RealtimeChatService chatService;
    private final ObjectMapper objectMapper;
    private final WebSocketMessageFactory messageFactory;

    /**
     * 当WebSocket连接建立时调用
     *
     * @param session WebSocket会话
     */
    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        logger.info("WebSocket连接已建立，SessionID: {}, 远程地址: {}",
            session.getId(), session.getRemoteAddress());

        // 从查询参数中获取角色设定
        URI uri = session.getUri();
        if (uri != null && uri.getQuery() != null) {
            String query = uri.getQuery();
            String characterId = extractParameter(query, CHARACTER_ID_PARAM);
            String personaPrompt = extractParameter(query, PERSONA_PROMPT_PARAM);

            // 将角色信息存储到会话属性中
            if (characterId != null) {
                session.getAttributes().put(CHARACTER_ID_PARAM, characterId);
            }
            if (personaPrompt != null) {
                session.getAttributes().put(PERSONA_PROMPT_PARAM, personaPrompt);
                logger.debug("会话 {} 设置角色提示词: {}", session.getId(), personaPrompt);
            }
        }

        // 设置默认角色提示词（如果未提供）
        if (!session.getAttributes().containsKey(PERSONA_PROMPT_PARAM)) {
            session.getAttributes().put(PERSONA_PROMPT_PARAM, DEFAULT_PERSONA_PROMPT);
        }

        logger.info("会话 {} 初始化完成", session.getId());
    }

    /**
     * 当接收到二进制消息时调用
     *
     * @param session WebSocket会话
     * @param message 二进制消息
     */
    @Override
    protected void handleBinaryMessage(WebSocketSession session, BinaryMessage message) {
        logger.debug("接收到会话 {} 的二进制消息，大小: {} bytes",
            session.getId(), message.getPayloadLength());

        try {
            // 将音频处理委托给服务层
            chatService.handleBinaryMessage(session, message);
        } catch (Exception e) {
            logger.error("处理会话 {} 的二进制消息时发生异常", session.getId(), e);
            sendErrorResponse(session, "音频处理失败，请重试");
        }
    }

    /**
     * 当接收到文本消息时调用
     *
     * @param session WebSocket会话
     * @param message 文本消息
     */
    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        String sessionId = session.getId();
        String textPayload = message.getPayload();

        logger.debug("接收到会话 {} 的文本消息: {}", sessionId, textPayload);

        try {

            // 流式处理文本消息
            String personaPrompt = (String) session.getAttributes().get(PERSONA_PROMPT_PARAM);

            // 调用流式方法，每收到文本块就立即发送给前端
            chatService.processTextChatStream(personaPrompt, textPayload, sessionId, chunk -> {
                try {
                    if (session.isOpen()) {
                        session.sendMessage(new TextMessage(chunk));
                        logger.trace("向会话 {} 发送文本块，长度: {}", sessionId, chunk.length());
                    }
                } catch (Exception e) {
                    logger.error("向会话 {} 发送文本块失败", sessionId, e);
                }
            });

            logger.debug("会话 {} 文本流式处理完成", sessionId);

        } catch (Exception e) {
            logger.error("处理会话 {} 的文本消息时发生异常", sessionId, e);
            sendErrorResponse(session, "文本处理失败，请重试");
        }
    }

    /**
     * 当WebSocket传输错误时调用
     *
     * @param session WebSocket会话
     * @param exception 错误信息
     */
    @Override
    public void handleTransportError(WebSocketSession session, @NotNull Throwable exception) {
        logger.error("WebSocket传输错误，SessionID: {}, 异常信息: {}",
            session.getId(), exception.getMessage(), exception);

        // 清理会话资源
        chatService.cleanupSession(session.getId());
    }

    /**
     * 当WebSocket连接关闭时调用
     *
     * @param session WebSocket会话
     * @param status 关闭状态
     */
    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        logger.info("WebSocket连接已关闭，SessionID: {}, 状态码: {}, 原因: {}",
            session.getId(), status.getCode(), status.getReason());

        // 清理会话相关资源
        chatService.cleanupSession(session.getId());
    }

    /**
     * 从查询字符串中提取参数
     * @param query 查询字符串
     * @param paramName 参数名
     * @return 参数值，如果不存在返回null
     */
    private String extractParameter(String query, String paramName) {
        if (query == null || query.isEmpty()) {
            return null;
        }

        String[] pairs = query.split("&");
        for (String pair : pairs) {
            String[] keyValue = pair.split("=", 2);
            if (keyValue.length == 2 && paramName.equals(keyValue[0])) {
                try {
                    return java.net.URLDecoder.decode(keyValue[1], StandardCharsets.UTF_8);
                } catch (Exception e) {
                    logger.warn("解码参数 {} 失败: {}", paramName, keyValue[1]);
                    return keyValue[1];
                }
            }
        }
        return null;
    }

    /**
     * 发送错误响应
     * @param session WebSocket会话
     * @param errorMessage 错误消息
     */
    private void sendErrorResponse(WebSocketSession session, String errorMessage) {
        try {
            if (session.isOpen()) {
                // 使用安全的 WebSocketMessageDTO 进行序列化，防止 JSON 注入攻击
                WebSocketMessageDTO messageDTO = messageFactory.createError(
                    errorMessage, session.getId());
                String jsonMessage = objectMapper.writeValueAsString(messageDTO);
                TextMessage errorMsg = new TextMessage(jsonMessage);
                session.sendMessage(errorMsg);
            }
        } catch (Exception e) {
            logger.error("发送错误响应失败", e);
        }
    }
}