package com.dotlinea.soulecho.client;

import java.util.List;
import java.util.function.Consumer;

/**
 * 大语言模型 (LLM) 服务客户端接口
 *
 * @author fanfan187
 * @version v1.0.0
 * @since v1.0.0
 */
public interface LLMClient {

    /**
     * 进行流式聊天对话
     * @param personaPrompt 角色设定提示词
     * @param history 对话历史
     * @param newText 最新用户输入
     * @param chunkConsumer 文本块消费者，每收到一块文本就会被调用
     */
    void chatStream(String personaPrompt, List<String> history, String newText, Consumer<String> chunkConsumer);

    /**
     * 进行流式聊天对话（支持知识库增强）
     * @param personaPrompt 角色设定提示词
     * @param history 对话历史
     * @param newText 最新用户输入
     * @param characterName 角色名称（用于知识库检索）
     * @param chunkConsumer 文本块消费者，每收到一块文本就会被调用
     */
    void chatStream(String personaPrompt, List<String> history, String newText, String characterName, Consumer<String> chunkConsumer);
}