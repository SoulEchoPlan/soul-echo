package com.dotlinea.soulecho.dto;

import lombok.Data;

/**
 * 角色数据传输对象
 * <p>
 * 用于在不同层之间传输角色数据
 * </p>
 *
 * @author fanfan187
 * @version v1.0.0
 * @since v1.0.0
 */
@Data
public class CharacterDTO {

    /**
     * 角色ID
     */
    private Long id;

    /**
     * 角色名称
     */
    private String name;

    /**
     * 角色描述
     */
    private String description;

    /**
     * 角色人设
     */
    private String personaPrompt;

    /**
     * 角色头像URL
     */
    private String avatarUrl;

    /**
     * 声音ID
     */
    private String voiceId;

    /**
     * 是否公开
     */
    private boolean isPublic = true;
}