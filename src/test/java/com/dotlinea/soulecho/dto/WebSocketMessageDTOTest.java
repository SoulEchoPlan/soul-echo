package com.dotlinea.soulecho.dto;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * WebSocketMessageDTO 单元测试
 * <p>
 * 只测试 DTO 本身的属性和注解，不涉及业务逻辑
 * </p>
 *
 * @author fanfan187
 * @version v1.0.0
 * @since v1.0.0
 */
class WebSocketMessageDTOTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void testGetterSetter() {
        // 测试所有 getter/setter 方法
        WebSocketMessageDTO dto = new WebSocketMessageDTO();
        
        // 测试设置和获取值
        dto.setType("test-type");
        dto.setContent("test content");
        dto.setTimestamp(1234567890L);
        dto.setSessionId("test-session");

        assertEquals("test-type", dto.getType());
        assertEquals("test content", dto.getContent());
        assertEquals(1234567890L, dto.getTimestamp());
        assertEquals("test-session", dto.getSessionId());
    }

    @Test
    void testAllArgsConstructor() {
        // 测试全参构造函数
        WebSocketMessageDTO dto = new WebSocketMessageDTO(
            "type", "content", "ERR-001", 1234567890L, "session-123"
        );

        assertEquals("type", dto.getType());
        assertEquals("content", dto.getContent());
        assertEquals("ERR-001", dto.getCode());
        assertEquals(1234567890L, dto.getTimestamp());
        assertEquals("session-123", dto.getSessionId());
    }

    @Test
    void testNoArgsConstructor() {
        // 测试无参构造函数
        WebSocketMessageDTO dto = new WebSocketMessageDTO();

        assertNull(dto.getType());
        assertNull(dto.getContent());
        assertNull(dto.getTimestamp());
        assertNull(dto.getSessionId());
    }

    @Test
    void testJsonIncludeNonNull() throws JsonProcessingException {
        // 测试 @JsonInclude(NON_NULL) 注解
        WebSocketMessageDTO dto = new WebSocketMessageDTO();
        dto.setType("test");
        // content, timestamp, sessionId 保持为 null

        String json = objectMapper.writeValueAsString(dto);

        // null 值不应该出现在 JSON 中
        assertTrue(json.contains("\"type\":\"test\""));
        assertFalse(json.contains("\"content\""));
        assertFalse(json.contains("\"timestamp\""));
        assertFalse(json.contains("\"sessionId\""));
    }

    @Test
    void testJsonSerializationWithNonNullValues() throws JsonProcessingException {
        // 测试包含非空值的 JSON 序列化
        WebSocketMessageDTO dto = new WebSocketMessageDTO(
            "message-type", "Hello World", null, System.currentTimeMillis(), "session-123"
        );

        String json = objectMapper.writeValueAsString(dto);

        // 所有非空值都应该出现在 JSON 中
        assertTrue(json.contains("\"type\":\"message-type\""));
        assertTrue(json.contains("\"content\":\"Hello World\""));
        assertTrue(json.contains("\"sessionId\":\"session-123\""));
        assertTrue(json.contains("\"timestamp\""));
        // code 为 null，不应该出现在 JSON 中
        assertFalse(json.contains("\"code\""));
    }

    @Test
    void testJsonDeserialization() throws JsonProcessingException {
        // 测试 JSON 反序列化
        String json = "{\"type\":\"test\",\"content\":\"hello\",\"timestamp\":1234567890,\"sessionId\":\"abc\"}";

        WebSocketMessageDTO dto = objectMapper.readValue(json, WebSocketMessageDTO.class);

        assertEquals("test", dto.getType());
        assertEquals("hello", dto.getContent());
        assertEquals(1234567890L, dto.getTimestamp());
        assertEquals("abc", dto.getSessionId());
    }

    @Test
    void testPartialJsonDeserialization() throws JsonProcessingException {
        // 测试部分字段反序列化
        String json = "{\"type\":\"partial\",\"content\":\"only two fields\"}";

        WebSocketMessageDTO dto = objectMapper.readValue(json, WebSocketMessageDTO.class);

        assertEquals("partial", dto.getType());
        assertEquals("only two fields", dto.getContent());
        // 未提供的字段应该为 null
        assertNull(dto.getTimestamp());
        assertNull(dto.getSessionId());
    }

    @Test
    void testEmptyJsonDeserialization() throws JsonProcessingException {
        // 测试空 JSON 对象反序列化
        String json = "{}";

        WebSocketMessageDTO dto = objectMapper.readValue(json, WebSocketMessageDTO.class);

        assertNull(dto.getType());
        assertNull(dto.getContent());
        assertNull(dto.getTimestamp());
        assertNull(dto.getSessionId());
    }
}