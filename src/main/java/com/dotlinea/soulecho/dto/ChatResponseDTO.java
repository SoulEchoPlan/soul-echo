package com.dotlinea.soulecho.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

/**
 * 聊天响应DTO
 *
 * @author fanfan187
 * @version v1.0.0
 * @since v1.0.0
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ChatResponseDTO {

    /**
     * 操作是否成功
     */
    private boolean success;

    /**
     * AI回复内容
     */
    private String reply;

    /**
     * 会话ID
     */
    private String sessionId;

    /**
     * 错误信息（当success为false时）
     */
    private String error;

    /**
     * 创建错误响应的静态方法
     */
    public static ChatResponseDTO error(String errorMessage) {
        ChatResponseDTO response = new ChatResponseDTO();
        response.setSuccess(false);
        response.setError(errorMessage);
        return response;
    }

    /**
     * 创建成功响应的静态方法
     */
    public static ChatResponseDTO success(String reply, String sessionId) {
        ChatResponseDTO response = new ChatResponseDTO();
        response.setSuccess(true);
        response.setReply(reply);
        response.setSessionId(sessionId);
        return response;
    }
}