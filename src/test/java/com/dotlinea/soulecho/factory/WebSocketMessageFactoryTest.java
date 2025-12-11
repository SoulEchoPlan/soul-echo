package com.dotlinea.soulecho.factory;

import com.dotlinea.soulecho.dto.WebSocketMessageDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * WebSocketMessageFactory 单元测试
 * <p>
 * 只测试工厂方法创建的对象属性正确性
 * </p>
 *
 * @author fanfan187
 * @version v1.0.0
 * @since v1.0.0
 */
class WebSocketMessageFactoryTest {

    private WebSocketMessageFactory messageFactory;

    @BeforeEach
    void setUp() {
        messageFactory = new WebSocketMessageFactory();
    }

    @Test
    void testCreateUserTranscription() {
        // 测试创建用户转写消息
        String content = "用户转写内容";
        String sessionId = "session-123";
        
        WebSocketMessageDTO dto = messageFactory.createUserTranscription(content, sessionId);

        // 验证各字段正确性
        assertEquals("user-transcription", dto.getType());
        assertEquals(content, dto.getContent());
        assertEquals(sessionId, dto.getSessionId());
        
        // 验证时间戳合理性
        assertNotNull(dto.getTimestamp());
        long currentTime = System.currentTimeMillis();
        assertTrue(dto.getTimestamp() <= currentTime);
        assertTrue(dto.getTimestamp() > currentTime - 1000); // 在1秒内
    }

    @Test
    void testCreateError() {
        // 测试创建错误消息
        String errorMessage = "发生错误了";
        String sessionId = "session-error-456";
        
        WebSocketMessageDTO dto = messageFactory.createError(errorMessage, sessionId);

        // 验证各字段正确性
        assertEquals("error", dto.getType());
        assertEquals(errorMessage, dto.getContent());
        assertEquals(sessionId, dto.getSessionId());
        
        // 验证时间戳合理性
        assertNotNull(dto.getTimestamp());
        long currentTime = System.currentTimeMillis();
        assertTrue(dto.getTimestamp() <= currentTime);
        assertTrue(dto.getTimestamp() > currentTime - 1000);
    }

    @Test
    void testCreateAIReply() {
        // 测试创建 AI 回复消息
        String replyText = "AI 回复内容";
        String sessionId = "session-ai-789";
        
        WebSocketMessageDTO dto = messageFactory.createAIReply(replyText, sessionId);

        // 验证各字段正确性
        assertEquals("ai-reply", dto.getType());
        assertEquals(replyText, dto.getContent());
        assertEquals(sessionId, dto.getSessionId());
        
        // 验证时间戳合理性
        assertNotNull(dto.getTimestamp());
        long currentTime = System.currentTimeMillis();
        assertTrue(dto.getTimestamp() <= currentTime);
        assertTrue(dto.getTimestamp() > currentTime - 1000);
    }

    @Test
    void testCreateAudioInfo() {
        // 测试创建音频信息消息
        String audioInfo = "audio.wav; duration: 10s; size: 1.2MB";
        String sessionId = "session-audio-001";
        
        WebSocketMessageDTO dto = messageFactory.createAudioInfo(audioInfo, sessionId);

        // 验证各字段正确性
        assertEquals("audio-info", dto.getType());
        assertEquals(audioInfo, dto.getContent());
        assertEquals(sessionId, dto.getSessionId());
        
        // 验证时间戳合理性
        assertNotNull(dto.getTimestamp());
        long currentTime = System.currentTimeMillis();
        assertTrue(dto.getTimestamp() <= currentTime);
        assertTrue(dto.getTimestamp() > currentTime - 1000);
    }

    @Test
    void testDifferentSessionIds() {
        // 测试不同 sessionId 的处理
        String content = "测试内容";
        String[] sessionIds = {"", "null", "session-1", "session-with-special-chars_123"};
        
        for (String sessionId : sessionIds) {
            WebSocketMessageDTO dto = messageFactory.createUserTranscription(content, sessionId);
            
            assertEquals(sessionId, dto.getSessionId());
            assertEquals(content, dto.getContent());
            assertEquals("user-transcription", dto.getType());
            assertNotNull(dto.getTimestamp());
        }
    }

    @Test
    void testSpecialContentHandling() {
        // 测试特殊内容的处理
        String[] specialContents = {
            "",
            "Normal text",
            "包含中文的内容",
            "Text with \n newlines",
            "Text with \"quotes\"",
            "Text with \\ backslashes",
            "Text with emojis 🎉",
            "JSON-like content {\"type\":\"hack\"}"
        };
        
        String sessionId = "test-session";
        
        for (String content : specialContents) {
            WebSocketMessageDTO dto = messageFactory.createUserTranscription(content, sessionId);
            
            assertEquals(content, dto.getContent());
            assertEquals(sessionId, dto.getSessionId());
            assertEquals("user-transcription", dto.getType());
            assertNotNull(dto.getTimestamp());
        }
    }

    @Test
    void testTimestampUniqueness() {
        // 测试时间戳的唯一性
        String content = "测试";
        String sessionId = "session-timestamp";
        
        WebSocketMessageDTO dto1 = messageFactory.createUserTranscription(content, sessionId);
        
        // 稍微等待一下确保时间戳不同
        try {
            Thread.sleep(1);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        WebSocketMessageDTO dto2 = messageFactory.createUserTranscription(content, sessionId);
        
        // 验证两个时间戳不同（或至少不递减）
        assertTrue(dto2.getTimestamp() >= dto1.getTimestamp());
        
        // 验证两个时间戳都是合理的
        long currentTime = System.currentTimeMillis();
        assertTrue(dto1.getTimestamp() <= currentTime);
        assertTrue(dto2.getTimestamp() <= currentTime);
    }

    @Test
    void testAllMessageTypesHaveDifferentTypes() {
        // 测试所有消息类型都有不同的 type 值
        String sessionId = "test-session";
        String content = "test content";
        
        WebSocketMessageDTO userDto = messageFactory.createUserTranscription(content, sessionId);
        WebSocketMessageDTO errorDto = messageFactory.createError(content, sessionId);
        WebSocketMessageDTO aiDto = messageFactory.createAIReply(content, sessionId);
        WebSocketMessageDTO audioDto = messageFactory.createAudioInfo(content, sessionId);
        
        // 验证所有类型都不同
        assertEquals("user-transcription", userDto.getType());
        assertEquals("error", errorDto.getType());
        assertEquals("ai-reply", aiDto.getType());
        assertEquals("audio-info", audioDto.getType());
        
        // 验证其他属性相同
        assertEquals(content, userDto.getContent());
        assertEquals(content, errorDto.getContent());
        assertEquals(content, aiDto.getContent());
        assertEquals(content, audioDto.getContent());
        
        assertEquals(sessionId, userDto.getSessionId());
        assertEquals(sessionId, errorDto.getSessionId());
        assertEquals(sessionId, aiDto.getSessionId());
        assertEquals(sessionId, audioDto.getSessionId());
    }
}