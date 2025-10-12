package com.dotlinea.soulecho.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import jakarta.validation.constraints.NotBlank;

/**
 * 聊天请求DTO
 *
 * @author fanfan187
 * @version v1.0.0
 * @since v1.0.0
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChatRequestDTO {

    /**
     * 消息内容
     */
    @NotBlank(message = "消息不能为空")
    private String message;

    /**
     * 角色ID（可选）
     */
    private Long characterId;

    /**
     * 会话ID（可选）
     */
    private String sessionId;
}