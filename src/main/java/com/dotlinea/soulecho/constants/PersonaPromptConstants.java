package com.dotlinea.soulecho.constants;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * 角色提示词常量
 * <p>
 * 统一管理所有默认角色提示词和系统提示词
 * </p>
 *
 * @author fanfan187
 * @version v1.0.0
 * @since v1.0.0
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class PersonaPromptConstants {

    /**
     * 默认角色提示词 - 标准版
     * <p>
     * 用于 WebSocket 连接和角色服务的默认 AI 助手设定
     * </p>
     */
    public static final String DEFAULT_PERSONA = "你是一个友好、有帮助的AI助手。请用自然、亲切的语气与用户对话。";

    /**
     * 默认角色提示词 - 简洁版
     * <p>
     * 用于特定场景的简化版本
     * </p>
     */
    public static final String DEFAULT_PERSONA_SIMPLE = "你是一个友好的AI助手，请用自然的方式与用户对话。";

    /**
     * 角色未找到时的提示词
     * <p>
     * 当用户请求不存在的角色时使用
     * </p>
     */
    public static final String CHARACTER_NOT_FOUND_PROMPT = "你好，你似乎想找一个我还不认识的角色。没关系，你可以先和我聊聊，或者在未来的版本中，我会学会如何创建新角色。现在，有什么可以帮你的吗？";

    /**
     * 默认角色名称
     */
    public static final String DEFAULT_CHARACTER_NAME = "default";
}