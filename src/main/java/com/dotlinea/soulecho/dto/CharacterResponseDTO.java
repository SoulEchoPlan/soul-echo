package com.dotlinea.soulecho.dto;

import java.time.LocalDateTime;

/**
 * @author fanfan187
 * @version v1.0.0
 * @since v1.0.0
 */
public record CharacterResponseDTO(
    Long id,
    String name,
    String personaPrompt,
    String avatarUrl,
    String voiceId,
    boolean isPublic,
    String knowledgeIndexId,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {}