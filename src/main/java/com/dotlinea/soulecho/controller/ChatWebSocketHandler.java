package com.dotlinea.soulecho.controller;

//import com.dotlinea.soulecho.service.RealtimeChatService;
import com.dotlinea.soulecho.assistant.SeparateChatAssistant;
import com.dotlinea.soulecho.entity.Smartvoice;
import com.dotlinea.soulecho.mapper.SmartvoiceMapper;
import com.dotlinea.soulecho.speechTranscriber.SpeechRecognition;
import com.dotlinea.soulecho.speechTranscriber.SpeechTranscriber;
import com.dotlinea.soulecho.speechTranscriber.Synthesis;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.AbstractWebSocketHandler;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.nio.ByteBuffer;
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
@Slf4j
public class ChatWebSocketHandler extends AbstractWebSocketHandler {

    private static final Logger logger = LoggerFactory.getLogger(ChatWebSocketHandler.class);
//    private final RealtimeChatService chatService;

    private final SmartvoiceMapper smartvoiceMapper;

    private final SeparateChatAssistant separateChatAssistant;
    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        logger.info("WebSocket连接已建立，SessionID: {}, 远程地址: {}",
            session.getId(), session.getRemoteAddress());

        // 从查询参数中获取角色设定
        URI uri = session.getUri();
        if (uri != null && uri.getQuery() != null) {
            String query = uri.getQuery();
            String characterId = extractParameter(query, "characterId");
            String personaPrompt = extractParameter(query, "personaPrompt");

            // 将角色信息存储到会话属性中
            if (characterId != null) {
                session.getAttributes().put("characterId", characterId);
            }
            if (personaPrompt != null) {
                session.getAttributes().put("personaPrompt", personaPrompt);
                logger.debug("会话 {} 设置角色提示词: {}", session.getId(), personaPrompt);
            }
        }

        // 设置默认角色提示词（如果未提供）
        if (!session.getAttributes().containsKey("personaPrompt")) {
            String defaultPrompt = "你是一个友好、有帮助的AI助手。请用自然、亲切的语气与用户对话。";
            session.getAttributes().put("personaPrompt", defaultPrompt);
        }

        logger.info("会话 {} 初始化完成", session.getId());
    }

    @Override
    protected void handleBinaryMessage(WebSocketSession session, BinaryMessage message) {
        logger.debug("接收到会话 {} 的二进制消息，大小: {} bytes",
            session.getId(), message.getPayloadLength());
        try {
            // 将音频处理委托给服务层
            //            chatService.handleBinaryMessage(session, message);
            Smartvoice smartvoice = smartvoiceMapper.selectById(1);
            // 从BinaryMessage中获取ByteBuffer
            ByteBuffer byteBuffer = message.getPayload();
            // 重置position到0，确保从数据开头读取
            byteBuffer.rewind();
            // 根据缓冲区剩余字节数创建数组
            byte[] audioData = new byte[byteBuffer.remaining()];
            // 将ByteBuffer中的数据复制到byte[]
            byteBuffer.get(audioData);
            String s = SpeechToText(audioData, smartvoice.getAppkey(), smartvoice.getToken());
        } catch (Exception e) {
            logger.error("处理会话 {} 的二进制消息时发生异常", session.getId(), e);
            sendErrorResponse(session, "音频处理失败，请重试");
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
//        String sessionId = session.getId();
//        String textPayload = message.getPayload();
//
//        logger.debug("接收到会话 {} 的文本消息: {}", sessionId, textPayload);
//
//        try {
//            // 处理文本消息
//            String personaPrompt = (String) session.getAttributes().get("personaPrompt");
//            String response = chatService.processTextChat(personaPrompt, textPayload, sessionId);
//
//            if (response != null && !response.trim().isEmpty()) {
//                // 发送文本回复
//                session.sendMessage(new TextMessage(response));
//                logger.debug("向会话 {} 发送文本回复: {}", sessionId, response);
//            } else {
//                sendErrorResponse(session, "无法生成回复，请重试");
//            }
//
//        } catch (Exception e) {
//            logger.error("处理会话 {} 的文本消息时发生异常", sessionId, e);
//            sendErrorResponse(session, "文本处理失败，请重试");
//        }
    }

    @Override
    public void handleTransportError(WebSocketSession session, @NotNull Throwable exception) {
        logger.error("WebSocket传输错误，SessionID: {}, 异常信息: {}",
            session.getId(), exception.getMessage(), exception);

        // 清理会话资源
//        chatService.cleanupSession(session.getId());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        logger.info("WebSocket连接已关闭，SessionID: {}, 状态码: {}, 原因: {}",
            session.getId(), status.getCode(), status.getReason());

        // 清理会话相关资源
//        chatService.cleanupSession(session.getId());
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
                TextMessage errorMsg = new TextMessage("{\"error\":\"" + errorMessage + "\"}");
                session.sendMessage(errorMsg);
            }
        } catch (Exception e) {
            logger.error("发送错误响应失败", e);
        }
    }
    //语音转文字
    public String SpeechToText(byte[] data,String appKey, String token){
        SpeechTranscriber speechTranscriber = new SpeechTranscriber(appKey,token,"");
        String message = speechTranscriber.process(data);
        speechTranscriber.shutdown();
        return message;
    }

    //ai对话
    public String AIConversation(Long memoryId,String message,String personaprompt){
        Flux<String> chat = separateChatAssistant.chat(memoryId,message,personaprompt);
        Mono<String> combinedMono = chat
                .collect(
                        () -> new StringBuilder(), // 初始化一个 StringBuilder
                        (sb, str) -> sb.append(str)  // 对每个字符串执行 append 操作
                )
                .map(StringBuilder::toString);
        String text = combinedMono.block();
        return text;
    }

    //文字转语音
    public byte[] TextToSpeech(String text,String appKey, String token,String voice){
        Synthesis synthesis = new Synthesis(appKey, token,"");
        byte[] data = synthesis.process(text,voice);
        synthesis.shutdown();
        return data;
    }
}