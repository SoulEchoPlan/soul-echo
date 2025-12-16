package com.dotlinea.soulecho.factory;

import com.dotlinea.soulecho.constants.MessageTypeConstants;
import com.dotlinea.soulecho.dto.WebSocketMessageDTO;
import org.springframework.stereotype.Component;

/**
 * WebSocket 消息工厂类
 * <p>
 * 负责创建各种类型的 WebSocket 消息
 * </p>
 *
 * @author fanfan187
 * @version v1.0.0
 * @since v1.0.0
 */
@Component
public class WebSocketMessageFactory {

    /**
     * 创建用户转写消息
     *
     * @param transcribedText 转写文本
     * @param sessionId       会话ID
     * @return WebSocket消息DTO
     */
    public WebSocketMessageDTO createUserTranscription(String transcribedText, String sessionId) {
        WebSocketMessageDTO dto = new WebSocketMessageDTO();
        dto.setType(MessageTypeConstants.USER_TRANSCRIPTION);
        dto.setContent(transcribedText);
        dto.setSessionId(sessionId);
        dto.setTimestamp(System.currentTimeMillis());
        return dto;
    }

    /**
     * 创建错误消息
     *
     * @param errorMessage 错误消息
     * @param sessionId    会话ID
     * @return WebSocket消息DTO
     */
    public WebSocketMessageDTO createError(String errorMessage, String sessionId) {
        WebSocketMessageDTO dto = new WebSocketMessageDTO();
        dto.setType(MessageTypeConstants.ERROR);
        dto.setContent(errorMessage);
        dto.setSessionId(sessionId);
        dto.setTimestamp(System.currentTimeMillis());
        return dto;
    }

    /**
     * 创建AI回复消息
     *
     * @param replyText 回复文本
     * @param sessionId 会话ID
     * @return WebSocket消息DTO
     */
    public WebSocketMessageDTO createAIReply(String replyText, String sessionId) {
        WebSocketMessageDTO dto = new WebSocketMessageDTO();
        dto.setType(MessageTypeConstants.AI_REPLY);
        dto.setContent(replyText);
        dto.setSessionId(sessionId);
        dto.setTimestamp(System.currentTimeMillis());
        return dto;
    }

    /**
     * 创建音频数据消息
     *
     * @param audioInfo 音频信息（如：格式、大小等）
     * @param sessionId 会话ID
     * @return WebSocket消息DTO
     */
    public WebSocketMessageDTO createAudioInfo(String audioInfo, String sessionId) {
        WebSocketMessageDTO dto = new WebSocketMessageDTO();
        dto.setType(MessageTypeConstants.AUDIO_INFO);
        dto.setContent(audioInfo);
        dto.setSessionId(sessionId);
        dto.setTimestamp(System.currentTimeMillis());
        return dto;
    }
}