package com.dotlinea.soulecho.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.nio.ByteBuffer;

/**
 * WebSocket 处理器
 * <p>
 * 该类继承自 TextWebSocketHandler，用于处理文本和二进制消息。
 * </p>
 *
 * @author fanfan187
 * @version v1.0.0
 * @since v1.0.0
 */
public class ChatWebSocketHandler extends TextWebSocketHandler {

    private static final Logger logger = LoggerFactory.getLogger(ChatWebSocketHandler.class);

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        logger.info("WebSocket已建立连接，ID: {}", session.getId());
        super.afterConnectionEstablished(session);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        logger.info("收到文本消息，SessionID: {}，内容: {}", session.getId(), message.getPayload());
        // TODO: 处理文本消息
        super.handleTextMessage(session, message);
    }

    @Override
    protected void handleBinaryMessage(WebSocketSession session, BinaryMessage message) {
        // 获取 BinaryMessage 的 ByteBuffer 载荷
        ByteBuffer payload = message.getPayload();

        // 将 ByteBuffer 转为 byte[]
        byte[] audioData = new byte[payload.remaining()];
        payload.get(audioData);

        // 记录收到的二进制数据长度
        logger.info("收到二进制消息，SessionID: {}，字节长度: {}", session.getId(), audioData.length);

        // TODO: 处理二进制数据，例如转发给 ASR 服务或保存到文件
    }

    @Override
    public boolean supportsPartialMessages() {
        // 返回 true 表示支持分片消息
        return true;
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        logger.error("WebSocket传输错误，SessionID: {}", session.getId(), exception);
        super.handleTransportError(session, exception);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        logger.info("WebSocket连接已关闭，SessionID: {}，状态: {}", session.getId(), status);
        super.afterConnectionClosed(session, status);
    }
}