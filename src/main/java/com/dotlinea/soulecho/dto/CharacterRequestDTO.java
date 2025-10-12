package com.dotlinea.soulecho.dto;

import jakarta.validation.constraints.NotBlank;
import org.hibernate.validator.constraints.URL;

/**
 * AI角色请求DTO
 *
 * @author fanfan187
 * @version v1.0.0
 * @since v1.0.0
 */
public record CharacterRequestDTO(
    @NotBlank(message = "角色名称不能为空")
    String name,

    @NotBlank(message = "角色设定不能为空")
    String personaPrompt,

    @URL(message = "头像必须是合法的URL")
    String avatarUrl,

    String voiceId,

    Boolean isPublic
) {}