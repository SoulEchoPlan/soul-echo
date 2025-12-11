package com.dotlinea.soulecho.security;

import com.dotlinea.soulecho.dto.WebSocketMessageDTO;
import com.dotlinea.soulecho.factory.WebSocketMessageFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * WebSocket 消息安全性测试
 * <p>
 * 专门测试 JSON 序列化安全性和注入攻击防护
 * </p>
 *
 * @author fanfan187
 * @version v1.0.0
 * @since v1.0.0
 */
class WebSocketMessageSecurityTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private WebSocketMessageFactory messageFactory;

    @BeforeEach
    void setUp() {
        messageFactory = new WebSocketMessageFactory();
    }

    @Test
    void testUserTranscriptionWithSafeContent() throws Exception {
        // 测试正常内容
        String safeContent = "你好，这是一个测试消息";
        WebSocketMessageDTO dto = messageFactory.createUserTranscription(safeContent, "session123");

        String json = objectMapper.writeValueAsString(dto);

        // 验证 JSON 格式正确
        assertTrue(json.contains("\"type\":\"user-transcription\""));
        assertTrue(json.contains("\"content\":\"你好，这是一个测试消息\""));
        assertTrue(json.contains("\"sessionId\":\"session123\""));

        // 验证可以安全反序列化
        WebSocketMessageDTO parsed = objectMapper.readValue(json, WebSocketMessageDTO.class);
        assertEquals("user-transcription", parsed.getType());
        assertEquals(safeContent, parsed.getContent());
        assertEquals("session123", parsed.getSessionId());
    }

    @Test
    void testUserTranscriptionWithDangerousContent() throws Exception {
        // 测试包含 JSON 注入攻击向量
        String dangerousContent = "Hello\"},\"type\":\"admin\",\"content\":\"hacked";
        WebSocketMessageDTO dto = messageFactory.createUserTranscription(dangerousContent, "session123");

        String json = objectMapper.writeValueAsString(dto);

        // 验证危险内容被正确转义，结构没有被破坏
        // Jackson 会自动转义双引号，所以我们期望看到转义后的形式
        assertTrue(json.contains("Hello\\\"},\\\"type\\\":\\\"admin\\\",\\\"content\\\":\\\"hacked"));
        // 验证类型字段没有被注入成功
        assertTrue(json.matches(".*\"type\":\"user-transcription\".*"));

        // 验证可以安全反序列化
        WebSocketMessageDTO parsed = objectMapper.readValue(json, WebSocketMessageDTO.class);
        assertEquals("user-transcription", parsed.getType());
        // 内容应该保持原样
        assertEquals(dangerousContent, parsed.getContent());
        assertEquals("session123", parsed.getSessionId());
    }

    @Test
    void testErrorMessageWithDangerousContent() throws Exception {
        // 测试错误消息中的危险内容
        String dangerousError = "Error: \"hack\"},\"type\":\"admin\"";
        WebSocketMessageDTO dto = messageFactory.createError(dangerousError, "session456");

        String json = objectMapper.writeValueAsString(dto);

        // 验证错误类型没有被篡改
        assertTrue(json.contains("\"type\":\"error\""));
        assertTrue(json.contains("\"content\":\"Error: \\\"hack\\\"},\\\"type\\\":\\\"admin\\\""));

        // 验证可以安全反序列化
        WebSocketMessageDTO parsed = objectMapper.readValue(json, WebSocketMessageDTO.class);
        assertEquals("error", parsed.getType());
        assertEquals(dangerousError, parsed.getContent());
        assertEquals("session456", parsed.getSessionId());
    }

    @Test
    void testSpecialCharacters() throws Exception {
        // 测试各种特殊字符
        String specialContent = "换行\n制表\t回车\r引号\"单引号'反斜杠\\ Unicode: 😊";
        WebSocketMessageDTO dto = messageFactory.createUserTranscription(specialContent, "session789");

        String json = objectMapper.writeValueAsString(dto);

        // 验证特殊字符被正确处理
        WebSocketMessageDTO parsed = objectMapper.readValue(json, WebSocketMessageDTO.class);
        assertEquals("user-transcription", parsed.getType());
        assertEquals(specialContent, parsed.getContent());
        assertEquals("session789", parsed.getSessionId());
    }

    @Test
    void testNullAndEmptyContent() throws Exception {
        // 测试空内容
        WebSocketMessageDTO dto1 = messageFactory.createUserTranscription("", "session1");
        String json1 = objectMapper.writeValueAsString(dto1);
        WebSocketMessageDTO parsed1 = objectMapper.readValue(json1, WebSocketMessageDTO.class);
        assertEquals("", parsed1.getContent());

        // 测试 null 内容（根据@JsonInclude(NON_NULL)应该被排除）
        WebSocketMessageDTO dto2 = new WebSocketMessageDTO();
        dto2.setType("test");
        dto2.setSessionId("session2");
        // content 为 null，应该不出现在 JSON 中
        String json2 = objectMapper.writeValueAsString(dto2);
        assertFalse(json2.contains("\"content\""));
    }

    @Test
    void testJsonStructure() throws Exception {
        // 测试 JSON 结构完整性
        WebSocketMessageDTO dto = messageFactory.createAIReply("测试回复", "session999");
        String json = objectMapper.writeValueAsString(dto);

        // 验证 JSON 结构完整且有序
        assertTrue(json.startsWith("{"));
        assertTrue(json.endsWith("}"));

        // 验证必要字段存在
        assertTrue(json.contains("\"type\":\"ai-reply\""));
        assertTrue(json.contains("\"content\":\"测试回复\""));
        assertTrue(json.contains("\"sessionId\":\"session999\""));
        assertTrue(json.contains("\"timestamp\""));

        // 验证时间戳是合理的
        WebSocketMessageDTO parsed = objectMapper.readValue(json, WebSocketMessageDTO.class);
        assertTrue(parsed.getTimestamp() > 0);
        assertTrue(parsed.getTimestamp() <= System.currentTimeMillis());
    }

    @Test
    void testJsonInjectionPrevention() throws Exception {
        // 模拟各种 JSON 注入攻击
        String[] injectionAttempts = {
            "Normal content\"},\"type\":\"admin\",\"content\":\"HACKED",
            "{\"type\":\"system\"},\"type\":\"user\"",
            "\n\"},\"type\":\"admin\",\"content\":\"Newline attack",
            "\\\"},\"type\":\"admin\",\"content\":\"Backslash attack"
        };

        for (String injectionAttempt : injectionAttempts) {
            WebSocketMessageDTO dto = messageFactory.createUserTranscription(injectionAttempt, "test-session");
            String json = objectMapper.writeValueAsString(dto);

            // 验证类型字段始终是 user-transcription，没有被注入
            assertTrue(json.contains("\"type\":\"user-transcription\""),
                "Injection attempt failed: " + injectionAttempt);

            // 验证可以安全反序列化
            WebSocketMessageDTO parsed = objectMapper.readValue(json, WebSocketMessageDTO.class);
            assertEquals("user-transcription", parsed.getType());
            assertEquals(injectionAttempt, parsed.getContent());
        }
    }
}