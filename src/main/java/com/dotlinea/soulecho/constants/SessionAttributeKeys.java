package com.dotlinea.soulecho.constants;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * WebSocket 会话属性键常量
 * <p>
 * 定义 WebSocket Session 中存储的属性键名
 * </p>
 *
 * @author fanfan187
 * @version v1.0.0
 * @since v1.0.0
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class SessionAttributeKeys {

    /**
     * 角色提示词属性键
     * <p>
     * 存储在 WebSocket Session 中的角色设定提示词
     * </p>
     */
    public static final String PERSONA_PROMPT = "personaPrompt";

    /**
     * 角色 ID 属性键
     * <p>
     * 存储在 WebSocket Session 中的角色 ID
     * </p>
     */
    public static final String CHARACTER_ID = "characterId";

    /**
     * 角色名称属性键
     * <p>
     * 存储在 WebSocket Session 中的角色名称
     * </p>
     */
    public static final String CHARACTER_NAME = "characterName";

    /**
     * TTS 启用状态属性键
     * <p>
     * 存储在 WebSocket Session 中的 TTS（文本转语音）开关状态
     * </p>
     */
    public static final String TTS_ENABLED = "tts_enabled";
}