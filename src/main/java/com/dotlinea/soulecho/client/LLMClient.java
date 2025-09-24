package com.dotlinea.soulecho.client;

import java.util.List;

/**
 * 大语言模型 (LLM) 服务客户端接口
 *
 * @author fanfan187
 * @version v1.0.0
 * @since v1.0.0
 */
public interface LLMClient {

    /**
     * 进行聊天对话
     * @param personaPrompt 角色设定提示词
     * @param history 对话历史
     * @param newText 最新用户输入
     * @return LLM生成的回复文本
     */
    String chat(String personaPrompt, List<String> history, String newText);
}