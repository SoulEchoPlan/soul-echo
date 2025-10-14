package com.dotlinea.soulecho.client.impl;

import com.dotlinea.soulecho.client.LLMClient;
import com.alibaba.dashscope.aigc.generation.Generation;
import com.alibaba.dashscope.aigc.generation.GenerationParam;
import com.alibaba.dashscope.aigc.generation.GenerationResult;
import com.alibaba.dashscope.common.Message;
import com.alibaba.dashscope.common.Role;
import com.alibaba.dashscope.exception.ApiException;
import com.alibaba.dashscope.exception.InputRequiredException;
import com.alibaba.dashscope.exception.NoApiKeyException;
import io.reactivex.Flowable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * LLM客户端实现（流式输出版本）
 *
 * @author fanfan187
 * @version v1.0.0
 * @since v1.0.0
 */
@Component
public class LLMClientImpl implements LLMClient {

    private static final Logger logger = LoggerFactory.getLogger(LLMClientImpl.class);

    @Value("${llm.api.key}")
    private String apiKey;

    @Value("${llm.model:qwen-plus}")
    private String modelName;

    @Value("${llm.max.tokens:2000}")
    private Integer maxTokens;

    @Value("${llm.temperature:0.8}")
    private Float temperature;

    private Generation generation;

    /**
     * 初始化阿里云LLM客户端
     * <p>
     * 该方法在对象创建完成后自动执行，用于初始化阿里云大语言模型客户端。
     * 初始化成功后将创建Generation实例并记录日志，如果初始化过程中出现异常，
     * 则记录错误日志并抛出运行时异常。
     * </p>
     *
     * @throws RuntimeException 当LLM客户端初始化失败时抛出
     */
    @PostConstruct
    public void init() {
        try {
            logger.info("初始化阿里云LLM客户端...");
            generation = new Generation();
            logger.info("阿里云LLM客户端初始化成功，使用模型: {}", modelName);
        } catch (Exception e) {
            logger.error("阿里云LLM客户端初始化失败", e);
            throw new RuntimeException("LLM客户端初始化异常", e);
        }
    }

    /**
     * LLM流式对话处理
     * @param personaPrompt 角色描述
     * @param history 历史对话
     * @param newText 新输入的文本
     * @param chunkConsumer 文本块消费者，每收到一块文本就会被调用
     */
    @Override
    public void chatStream(String personaPrompt, List<String> history, String newText, Consumer<String> chunkConsumer) {
        if (newText == null || newText.trim().isEmpty()) {
            logger.warn("接收到空的用户输入");
            return;
        }

        if (chunkConsumer == null) {
            logger.warn("文本块消费者为空");
            return;
        }

        try {
            List<Message> messages = buildMessages(personaPrompt, history, newText);
            callAliyunLLMStream(messages, chunkConsumer);
        } catch (Exception e) {
            logger.error("LLM流式对话处理失败", e);
            chunkConsumer.accept("抱歉，我现在无法回应您的消息，请稍后再试。");
        }
    }

    /**
     * 构建消息列表
     * <p>
     * 该方法用于构建消息列表，将角色描述、历史对话和用户输入的文本构建成消息列表。
     * </p>
     *
     * @param personaPrompt 角色描述
     * @param history       历史对话
     * @param newText       新输入的文本
     * @return 消息列表
     */
    private List<Message> buildMessages(String personaPrompt, List<String> history, String newText) {
        List<Message> messages = new ArrayList<>();

        if (personaPrompt != null && !personaPrompt.trim().isEmpty()) {
            messages.add(Message.builder()
                .role(Role.SYSTEM.getValue())
                .content(personaPrompt)
                .build());
        }

        if (history != null && !history.isEmpty()) {
            boolean isUser = true;
            for (String historyItem : history) {
                if (historyItem != null && !historyItem.trim().isEmpty()) {
                    messages.add(Message.builder()
                        .role(isUser ? Role.USER.getValue() : Role.ASSISTANT.getValue())
                        .content(historyItem.trim())
                        .build());
                    isUser = !isUser;
                }
            }
        }

        messages.add(Message.builder()
            .role(Role.USER.getValue())
            .content(newText.trim())
            .build());

        return messages;
    }

    /**
     * 调用阿里云大语言模型（LLM）流式接口
     * @param messages 用户与系统的对话消息列表
     * @param chunkConsumer 文本块消费者
     */
    private void callAliyunLLMStream(List<Message> messages, Consumer<String> chunkConsumer) {
        try {
            // 构建参数，启用流式模式
            GenerationParam param = GenerationParam.builder()
                    .apiKey(apiKey)
                    .model(modelName)
                    .messages(messages)
                    .maxTokens(maxTokens)
                    .temperature(temperature)
                    .topP(0.8)
                    .repetitionPenalty(1.1f)
                    .resultFormat(GenerationParam.ResultFormat.MESSAGE)
                    .incrementalOutput(true)  // 启用增量输出
                    .build();

            logger.debug("调用阿里云LLM流式接口，模型: {}, 消息数量: {}", modelName, messages.size());

            // 调用流式接口
            Flowable<GenerationResult> resultFlowable = generation.streamCall(param);

            // 订阅流式结果，逐块处理
            resultFlowable.blockingForEach(result -> {
                if (result.getOutput() != null &&
                    result.getOutput().getChoices() != null &&
                    !result.getOutput().getChoices().isEmpty()) {

                    String content = result.getOutput().getChoices().get(0).getMessage().getContent();

                    if (content != null && !content.isEmpty()) {
                        logger.trace("收到LLM文本块，长度: {}", content.length());
                        // 立即将文本块传递出去
                        chunkConsumer.accept(content);
                    }
                }
            });

            logger.info("LLM流式生成完成");

        } catch (NoApiKeyException e) {
            logger.error("API密钥未配置或无效", e);
            chunkConsumer.accept("服务配置异常，请联系管理员。");
        } catch (InputRequiredException e) {
            logger.error("输入参数不完整", e);
            chunkConsumer.accept("输入信息不完整，请重新输入。");
        } catch (ApiException e) {
            logger.error("阿里云API调用异常，错误码: {}, 错误信息: {}",
                e.getStatus().getStatusCode(), e.getMessage(), e);
            chunkConsumer.accept("服务暂时不可用，请稍后重试。");
        } catch (Exception e) {
            logger.error("LLM流式调用发生未知异常", e);
            chunkConsumer.accept("抱歉，出现了一些技术问题，请稍后再试。");
        }
    }
}