package com.dotlinea.soulecho.service.impl;

import com.dotlinea.soulecho.client.ASRClient;
import com.dotlinea.soulecho.client.LLMClient;
import com.dotlinea.soulecho.client.TTSClient;
import com.dotlinea.soulecho.constants.MessageTypeConstants;
import com.dotlinea.soulecho.constants.PersonaPromptConstants;
import com.dotlinea.soulecho.constants.RedisKeyConstants;
import com.dotlinea.soulecho.constants.SessionAttributeKeys;
import com.dotlinea.soulecho.dto.WebSocketMessageDTO;
import com.dotlinea.soulecho.exception.ASRException;
import com.dotlinea.soulecho.factory.WebSocketMessageFactory;
import com.dotlinea.soulecho.service.RealtimeChatService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.redisson.api.RList;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

/**
 * 实时聊天服务实现类
 * <p>
 * 封装复杂的业务逻辑，处理WebSocket会话的完整流程：
 * 音频识别 -> 文本对话 -> 语音合成 -> 响应发送
 * </p>
 *
 * @author fanfan187
 * @version v1.0.0
 * @since v1.0.0
 */
@Service
@RequiredArgsConstructor
public class RealtimeChatServiceImpl implements RealtimeChatService {

    private static final Logger logger = LoggerFactory.getLogger(RealtimeChatServiceImpl.class);

    /**
     * 语音端点检测静默超时时间 (毫秒)
     * <p>
     * 从1.2秒调整为1.8秒，优化语音识别体验：
     * <ul>
     * <li>避免用户说话短暂停顿时误判为说话结束</li>
     * <li>给用户更自然的语音交互节奏</li>
     * <li>减少误触发导致的锁竞争</li>
     * </ul>
     * </p>
     */
    private static final long SILENCE_TIMEOUT_MS = 1800;

    private final ASRClient asrClient;
    private final LLMClient llmClient;
    private final TTSClient ttsClient;
    private final ObjectMapper objectMapper;
    private final WebSocketMessageFactory messageFactory;
    private final RedissonClient redissonClient;

    /**
     * 异步处理线程池（由 Spring 管理，避免 OOM）
     * 使用 @Qualifier 注解指定注入 chatExecutor Bean
     */
    @Qualifier("chatExecutor")
    private final Executor chatExecutor;

    /**
     * 音频缓冲区 - 存储每个会话正在接收的音频数据
     * 保留在本地内存，因为音频流是高频小包，且 WebSocket 连接是粘性的
     */
    private final Map<String, AudioBuffer> audioBuffers = new ConcurrentHashMap<>();

    /**
     * 定时器线程池，用于语音端点检测
     */
    private final ScheduledExecutorService scheduledExecutor = Executors.newScheduledThreadPool(4);

    @Override
    public void handleBinaryMessage(WebSocketSession session, BinaryMessage message) {
        String sessionId = session.getId();
        logger.debug("处理会话 {} 的二进制音频消息，数据大小: {} bytes",
                sessionId, message.getPayloadLength());

        ByteBuffer audioPayload = cloneAudioBuffer(message.getPayload());
        if (audioPayload == null || !audioPayload.hasRemaining()) {
            logger.warn("会话 {} 收到的音频数据为空或不可用", sessionId);
            sendErrorMessage(session, "音频数据无效，请重试");
            return;
        }

        // 获取或创建会话的音频缓冲区
        AudioBuffer audioBuffer = audioBuffers.computeIfAbsent(sessionId, k -> new AudioBuffer());

        synchronized (audioBuffer) {
            // 将音频数据追加到缓冲区
            audioBuffer.append(audioPayload);

            // 取消之前的静默检测定时器
            if (audioBuffer.silenceDetectionTask != null) {
                audioBuffer.silenceDetectionTask.cancel(false);
            }

            // 启动新的静默检测定时器
            audioBuffer.silenceDetectionTask = scheduledExecutor.schedule(() -> {
                // 超时触发，认为用户说话结束
                handleSpeechEnd(session);
            }, SILENCE_TIMEOUT_MS, TimeUnit.MILLISECONDS);

            logger.trace("会话 {} 音频缓冲区大小: {} bytes", sessionId, audioBuffer.size());
        }
    }

    /**
     * 处理用户说话结束事件
     * @param session WebSocket会话
     */
    private void handleSpeechEnd(WebSocketSession session) {
        String sessionId = session.getId();
        AudioBuffer audioBuffer = audioBuffers.get(sessionId);

        if (audioBuffer == null) {
            return;
        }

        byte[] audioData;
        synchronized (audioBuffer) {
            if (audioBuffer.size() == 0) {
                return;
            }

            // 获取完整的音频数
            audioData = audioBuffer.toByteArray();

            // 清空缓冲区，为下一轮语音做准备
            audioBuffer.clear();
        }

        logger.info("会话 {} 检测到说话结束，音频数据大小: {} bytes", sessionId, audioData.length);

        // 异步处理完整的音频数据（使用 Spring 管理的线程池）
        chatExecutor.execute(() -> processAudioMessage(session, audioData));
    }

    /**
     * 处理完整音频消息的核心流程: ASR -> LLM -> TTS (全链路异步化)
     * <p>
     * 使用非阻塞式分布式锁确保同一会话的消息顺序处理，避免并发冲突。
     * 如果无法立即获取锁，则拒绝本次请求，避免阻塞线程池。
     * </p>
     *
     * @param session WebSocket会话
     * @param audioData 完整的音频数据
     */
    private void processAudioMessage(WebSocketSession session, byte[] audioData) {
        String sessionId = session.getId();
        RLock sessionLock = getSessionLock(sessionId);

        // 尝试非阻塞获取分布式锁
        boolean lockAcquired;
        try {
            lockAcquired = sessionLock.tryLock(3000, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            logger.warn("会话 {} 尝试获取锁时被中断", sessionId);
            Thread.currentThread().interrupt();
            sendErrorMessage(session, "系统繁忙，请稍后重试");
            return;
        }

        if (!lockAcquired) {
            logger.warn("检测到语音片段重叠，忽略本次尾部数据");
            return;
        }

        try {
            // === 步骤1: 异步语音识别 (ASR) ===
            logger.debug("会话 {} 开始异步语音识别", sessionId);
            ByteArrayInputStream audioInputStream = new ByteArrayInputStream(audioData);

            // 调用异步ASR，返回CompletableFuture
            asrClient.recognizeAsync(audioInputStream)
                    .thenAccept(recognizedText -> {
                        // ASR成功回调
                        if (recognizedText == null || recognizedText.trim().isEmpty()) {
                            logger.debug("会话 {} 语音识别无结果", sessionId);
                            return;
                        }

                        // 新增：严格校验 ASR 结果有效性，过滤幻觉
                        String filteredText = recognizedText.trim();
                        if (filteredText.length() < 2 && isPunctuationOnly(filteredText)) {
                            logger.warn("会话 {} ASR 返回无效文本（仅有标点符号或过短）: {}，忽略处理", sessionId, filteredText);
                            return;
                        }

                        logger.info("会话 {} 识别结果: {}", sessionId, recognizedText);

                        // === 步骤2: 发送用户转写文本回显 ===
                        sendUserTranscriptionEcho(session, recognizedText);

                        // === 步骤3: 获取角色设定 ===
                        String personaPrompt = getPersonaPrompt(session);
                        String characterName = getCharacterName(session);

                        // === 步骤4: LLM流式对话生成 + 句子级流式TTS ===
                        logger.debug("会话 {} 开始 LLM 流式对话生成", sessionId);

                        try {
                            // 从 session 读取 TTS 状态，不硬编码
                            Boolean ttsEnabled = (Boolean) session.getAttributes().get(SessionAttributeKeys.TTS_ENABLED);
                            // 如果未设置，默认开启 TTS（根据用户需求）
                            boolean actualTtsState = (ttsEnabled != null) ? ttsEnabled : true;

                            logger.debug("会话 {} 从 Session 读取 TTS 状态: {}", sessionId, actualTtsState);
                            streamLlmResponseWithTts(personaPrompt, recognizedText, sessionId, characterName, actualTtsState, session);
                            logger.info("会话 {} 完整音频处理流程结束", sessionId);
                        } catch (Exception e) {
                            logger.error("会话 {} LLM流式对话处理失败", sessionId, e);
                            sendErrorMessage(session, "生成回复时发生错误，请稍后重试");
                        }
                    })
                    .exceptionally(throwable -> {
                        // 全链路异常处理，根据异常类型给出不同的用户提示
                        Throwable rootCause = throwable.getCause() != null ? throwable.getCause() : throwable;
                        logger.error("会话 {} 异步处理音频消息时发生异常", sessionId, throwable);

                        // 判断异常类型，给出相应的用户提示
                        String userMessage;
                        if (rootCause instanceof ASRException asrEx) {
                            userMessage = asrEx.getUserFriendlyMessage();

                            // 如果是免费试用过期，额外记录详细的日志
                            if (asrEx.isTrialExpired()) {
                                logger.error("ASR服务免费试用已到期 - 请访问阿里云控制台开通服务: " +
                                        "https://nls-portal.console.aliyun.com/");
                            }
                        } else if (rootCause instanceof CompletionException && rootCause.getCause() instanceof ASRException asrEx) {
                            // 处理嵌套的 CompletionException
                            userMessage = asrEx.getUserFriendlyMessage();
                        } else {
                            // 其他未知异常，使用通用提示
                            userMessage = "处理您的消息时发生错误，请稍后重试。";
                        }

                        sendErrorMessage(session, userMessage);
                        return null;
                    })
                    .whenComplete((result, throwable) -> {
                        // 确保在任何情况下都释放锁
                        try {
                            sessionLock.unlock();
                            logger.debug("会话 {} 释放分布式锁", sessionId);
                        } catch (IllegalMonitorStateException e) {
                            logger.warn("会话 {} 尝试释放未持有的锁（可能已释放）", sessionId);
                        }
                    });

        } catch (Exception e) {
            logger.error("会话 {} 启动异步处理时发生异常", sessionId, e);
            sendErrorMessage(session, "处理您的消息时发生错误，请稍后重试。");

            // 确保异常情况下释放锁
            try {
                sessionLock.unlock();
            } catch (IllegalMonitorStateException ex) {
                logger.error("会话 {} 异常情况下释放锁失败", sessionId, ex);
            }
        }
    }

    @Override
    public void processTextChatStream(String personaPrompt, String userInput, String sessionId, java.util.function.Consumer<String> chunkConsumer) {
        processTextChatStream(personaPrompt, userInput, sessionId, null, chunkConsumer);
    }

    @Override
    public void processTextChatStream(String personaPrompt, String userInput, String sessionId, String characterName, java.util.function.Consumer<String> chunkConsumer) {
        if (userInput == null || userInput.trim().isEmpty()) {
            return;
        }

        try {
            // 获取或创建会话历史
            List<String> history = getSessionHistory(sessionId);

            // StringBuilder 用于累积完整的 LLM 响应
            StringBuilder fullResponse = new StringBuilder();

            // 调用 LLM 流式生成，逐块处理
            if (characterName != null && !characterName.trim().isEmpty()) {
                // 使用支持知识库的方法
                llmClient.chatStream(personaPrompt, history, userInput, characterName, chunk -> {
                    fullResponse.append(chunk);
                    // 将文本块透传给消费者
                    if (chunkConsumer != null) {
                        chunkConsumer.accept(chunk);
                    }
                });
            } else {
                // 使用普通方法
                llmClient.chatStream(personaPrompt, history, userInput, chunk -> {
                    fullResponse.append(chunk);
                    // 将文本块透传给消费者
                    if (chunkConsumer != null) {
                        chunkConsumer.accept(chunk);
                    }
                });
            }

            // LLM 流式生成完成后，更新会话历史
            String response = fullResponse.toString();
            if (!response.trim().isEmpty()) {
                history.add(userInput);
                history.add(response);

                // 限制历史长度，避免内存过度使用
                if (history.size() > 20) {
                    history.subList(0, history.size() - 20).clear();
                }
            }

        } catch (Exception e) {
            logger.error("会话 {} 流式文本对话处理失败", sessionId, e);
            if (chunkConsumer != null) {
                chunkConsumer.accept("抱歉，处理您的消息时遇到了问题。");
            }
        }
    }

    @Override
    public void processTextChatStream(String personaPrompt, String userInput, String sessionId,
                                     String characterName, boolean enableTts,
                                     java.util.function.Consumer<String> chunkConsumer) {
        if (userInput == null || userInput.trim().isEmpty()) {
            return;
        }

        try {
            // 获取或创建会话历史
            List<String> history = getSessionHistory(sessionId);

            // StringBuilder 用于累积完整的 LLM 响应
            StringBuilder fullResponse = new StringBuilder();

            // 句子级 TTS 缓冲区（仅当 enableTts=true 时使用）
            StringBuilder sentenceBuffer = enableTts ? new StringBuilder() : null;
            java.util.regex.Pattern pattern = enableTts
                ? java.util.regex.Pattern.compile("[^.!?。！？]+[.!?。！？]")
                : null;

            // 定义 LLM 文本块处理器
            Consumer<String> llmChunkHandler = chunk -> {
                // 1. 累积完整响应
                fullResponse.append(chunk);

                // 2. 透传给调用方的 chunkConsumer（兼容原有行为）
                if (chunkConsumer != null) {
                    chunkConsumer.accept(chunk);
                }

                // 3. 如果启用 TTS，检测完整句子并触发语音合成
                // ⚠️ 注意：此方法无 session 参数，因此无法发送音频响应或错误通知
                //    此处仅展示逻辑框架，实际使用中应通过 WebSocket 调用 streamLlmResponseWithTts
                if (enableTts && sentenceBuffer != null && pattern != null) {
                    logger.warn("processTextChatStream(Consumer, enableTts=true) 模式下不支持 TTS，" +
                              "请通过 WebSocket 使用 streamLlmResponseWithTts 方法");
                }
            };

            // 调用 LLM 流式生成
            if (characterName != null && !characterName.trim().isEmpty()) {
                llmClient.chatStream(personaPrompt, history, userInput, characterName, llmChunkHandler);
            } else {
                llmClient.chatStream(personaPrompt, history, userInput, llmChunkHandler);
            }

            // 更新会话历史
            String response = fullResponse.toString();
            if (!response.trim().isEmpty()) {
                history.add(userInput);
                history.add(response);

                // 限制历史长度
                if (history.size() > 20) {
                    history.subList(0, history.size() - 20).clear();
                }
            }

        } catch (Exception e) {
            logger.error("会话 {} 流式文本对话处理失败", sessionId, e);
            if (chunkConsumer != null) {
                chunkConsumer.accept("抱歉，处理您的消息时遇到了问题。");
            }
        }
    }

    @Override
    public void handleTextRequest(WebSocketSession session, String userInput, boolean enableTts) {
        // 从 session 中获取角色信息
        String personaPrompt = (String) session.getAttributes().get(SessionAttributeKeys.PERSONA_PROMPT);
        String characterName = (String) session.getAttributes().get(SessionAttributeKeys.CHARACTER_NAME);

        // 直接调用流式处理方法（支持 TTS）
        streamLlmResponseWithTts(personaPrompt, userInput, session.getId(), characterName, enableTts, session);
    }

    /**
     * 流式生成 LLM 响应并支持可选的 TTS 语音合成
     * <p>
     * 核心逻辑：
     * 1. 调用 LLM 流式生成文本
     * 2. 实时将文本块推送给前端
     * 3. 如果 enableTts=true，累积文本并检测完整句子，调用 TTS
     * 4. TTS 失败时只记录日志并发送错误通知，不阻断文本生成
     * </p>
     *
     * @param personaPrompt 角色设定
     * @param userInput 用户输入
     * @param sessionId 会话ID
     * @param characterName 角色名称（用于知识库检索，可为 null）
     * @param enableTts 是否启用 TTS 语音合成
     * @param session WebSocket 会话（用于发送响应，enableTts=true 时必填）
     */
    private void streamLlmResponseWithTts(
            String personaPrompt,
            String userInput,
            String sessionId,
            String characterName,
            boolean enableTts,
            WebSocketSession session) {

        if (userInput == null || userInput.trim().isEmpty()) {
            return;
        }

        // TTS 熔断标志位：记录 TTS 服务是否已损坏
        final boolean[] ttsCircuitBreaker = {false};
        // 标志位：记录是否已发送过错误通知（避免重复发送）
        final boolean[] errorSent = {false};

        try {
            // 获取或创建会话历史
            List<String> history = getSessionHistory(sessionId);
            StringBuilder fullResponse = new StringBuilder();

            // 句子级 TTS 缓冲区（仅当 enableTts=true 时使用）
            StringBuilder sentenceBuffer = enableTts ? new StringBuilder() : null;
            java.util.regex.Pattern pattern = enableTts
                ? java.util.regex.Pattern.compile("[^.!?。！？]+[.!?。！？]")
                : null;

            // 定义 LLM 文本块处理器
            Consumer<String> llmChunkHandler = chunk -> {
                // 1. 累积完整响应（用于更新会话历史）
                fullResponse.append(chunk);

                // 2. 实时推送文本块到前端（无论 TTS 是否失败都要发送文字）
                try {
                    if (session != null && session.isOpen()) {
                        session.sendMessage(new TextMessage(chunk));
                        logger.trace("向会话 {} 发送文本块: {}", sessionId, chunk);
                    }
                } catch (IOException e) {
                    logger.error("向会话 {} 发送文本块失败", sessionId, e);
                }

                // 3. 如果启用 TTS，检测完整句子并触发语音合成
                if (enableTts && sentenceBuffer != null && pattern != null && !ttsCircuitBreaker[0]) {
                    sentenceBuffer.append(chunk);
                    String bufferedText = sentenceBuffer.toString();
                    java.util.regex.Matcher matcher = pattern.matcher(bufferedText);

                    int lastMatchEnd = 0;
                    while (matcher.find() && !ttsCircuitBreaker[0]) {
                        String completeSentence = matcher.group();
                        lastMatchEnd = matcher.end();

                        logger.debug("会话 {} 提取完整句子: {}", sessionId, completeSentence);

                        // 强制异常隔离：TTS 异常不阻断 LLM 文本流式推送
                        try {
                            ttsClient.synthesize(completeSentence, audioChunk ->
                                sendAudioResponse(session, audioChunk)
                            );
                        } catch (com.dotlinea.soulecho.exception.TTSException e) {
                            // 触发熔断
                            ttsCircuitBreaker[0] = true;
                            // 只在第一次熔断时发送错误通知（避免刷屏）
                            if (!errorSent[0]) {
                                // 根据异常类型提供精确的用户提示
                                String userMessage = e.getUserFriendlyMessage();
                                logger.warn("会话 {} TTS 合成失败: {}", sessionId, userMessage, e);
                                errorSent[0] = true;
                                // 构造标准错误消息并发送
                                try {
                                    WebSocketMessageDTO errorMessage = messageFactory.createErrorWithCode(
                                        userMessage,
                                        MessageTypeConstants.TTS_BROKEN,
                                        sessionId
                                    );
                                    String jsonMessage = objectMapper.writeValueAsString(errorMessage);
                                    session.sendMessage(new TextMessage(jsonMessage));
                                } catch (IOException ioException) {
                                    logger.error("向会话 {} 发送 TTS 熔断通知失败", sessionId, ioException);
                                }
                            }
                            // 绝对禁止再次 throw e，吞掉异常让代码继续执行
                        } catch (Exception e) {
                            // 其他未预期的异常
                            logger.error("会话 {} TTS处理时发生未预期异常", sessionId, e);
                            ttsCircuitBreaker[0] = true;
                            if (!errorSent[0]) {
                                errorSent[0] = true;
                                sendErrorMessage(session, "语音服务异常，已切换至文字模式");
                            }
                        }
                    }

                    // 移除已处理的句子，保留未完成的部分
                    if (lastMatchEnd > 0) {
                        sentenceBuffer.delete(0, lastMatchEnd);
                    }
                }
            };

            // 调用 LLM 流式生成
            if (characterName != null && !characterName.trim().isEmpty()) {
                llmClient.chatStream(personaPrompt, history, userInput, characterName, llmChunkHandler);
            } else {
                llmClient.chatStream(personaPrompt, history, userInput, llmChunkHandler);
            }

            // 处理剩余的不成句内容（TTS 模式下）
            if (enableTts && sentenceBuffer != null && !ttsCircuitBreaker[0]) {
                String remainingText = sentenceBuffer.toString().trim();
                if (!remainingText.isEmpty()) {
                    logger.debug("会话 {} 处理剩余文本: {}", sessionId, remainingText);

                    // 强制异常隔离：TTS 异常不阻断 LLM 文本流式推送
                    try {
                        ttsClient.synthesize(remainingText, audioChunk ->
                            sendAudioResponse(session, audioChunk)
                        );
                    } catch (com.dotlinea.soulecho.exception.TTSException e) {
                        // 触发熔断
                        ttsCircuitBreaker[0] = true;
                        // 只在第一次熔断时发送错误通知（避免刷屏）
                        if (!errorSent[0]) {
                            // 根据异常类型提供精确的用户提示
                            String userMessage = e.getUserFriendlyMessage();
                            logger.warn("会话 {} TTS 合成剩余文本失败: {}", sessionId, userMessage, e);
                            errorSent[0] = true;
                            // 构造标准错误消息并发送
                            try {
                                WebSocketMessageDTO errorMessage = messageFactory.createErrorWithCode(
                                    userMessage,
                                    MessageTypeConstants.TTS_BROKEN,
                                    sessionId
                                );
                                String jsonMessage = objectMapper.writeValueAsString(errorMessage);
                                session.sendMessage(new TextMessage(jsonMessage));
                            } catch (IOException ioException) {
                                logger.error("向会话 {} 发送 TTS 熔断通知失败", sessionId, ioException);
                            }
                        }
                        // 绝对禁止再次 throw e，吞掉异常让代码继续执行
                    } catch (Exception e) {
                        // 其他未预期的异常
                        logger.error("会话 {} TTS处理时发生未预期异常", sessionId, e);
                        ttsCircuitBreaker[0] = true;
                        if (!errorSent[0]) {
                            errorSent[0] = true;
                            sendErrorMessage(session, "语音服务异常，已切换至文字模式");
                        }
                    }
                }
            }

            // 如果 TTS 失败，从历史记录中移除本次回复（避免显示不完整的对话）
            if (ttsCircuitBreaker[0]) {
                logger.warn("会话 {} TTS 失败，不保存本次对话到历史记录", sessionId);
            } else {
                // 更新会话历史
                String response = fullResponse.toString();
                if (!response.trim().isEmpty()) {
                    history.add(userInput);
                    history.add(response);

                    // 限制历史长度，避免内存过度使用
                    if (history.size() > 20) {
                        history.subList(0, history.size() - 20).clear();
                    }
                }
            }

        } catch (Exception e) {
            logger.error("会话 {} 流式文本对话处理失败", sessionId, e);
            // 向前端发送友好错误提示
            try {
                if (session != null && session.isOpen()) {
                    session.sendMessage(new TextMessage("抱歉，处理您的消息时遇到了问题。"));
                }
            } catch (IOException ioException) {
                logger.error("向会话 {} 发送错误提示失败", sessionId, ioException);
            }
        }
    }

    @Override
    public void cleanupSession(String sessionId) {
        logger.info("清理会话 {} 的资源", sessionId);

        // 清理 Redis 中的会话历史
        String historyKey = RedisKeyConstants.SESSION_HISTORY_PREFIX + sessionId;
        RList<String> sessionHistory = redissonClient.getList(historyKey);
        sessionHistory.delete();
        logger.debug("已删除会话 {} 的 Redis 历史记录", sessionId);

        // 清理 Redis 中的会话锁（如果存在且未被占用）
        String lockKey = RedisKeyConstants.SESSION_LOCK_PREFIX + sessionId;
        RLock sessionLock = redissonClient.getLock(lockKey);
        // 分布式锁会自动过期，这里主要是立即释放（如果当前线程持有）
        if (sessionLock.isHeldByCurrentThread()) {
            sessionLock.unlock();
            logger.debug("已释放会话 {} 的分布式锁", sessionId);
        }

        // 清理本地内存中的音频缓冲区
        AudioBuffer audioBuffer = audioBuffers.remove(sessionId);
        if (audioBuffer != null && audioBuffer.silenceDetectionTask != null) {
            audioBuffer.silenceDetectionTask.cancel(false);
            logger.debug("已清理会话 {} 的音频缓冲区", sessionId);
        }
    }

    /**
     * 获取会话的角色设定
     * @param session WebSocket会话
     * @return 角色设定提示词
     */
    private String getPersonaPrompt(WebSocketSession session) {
        // 从会话属性中获取角色设定
        Object personaPrompt = session.getAttributes().get(SessionAttributeKeys.PERSONA_PROMPT);
        if (personaPrompt instanceof String promptString) {
            return promptString;
        }

        // 默认角色设定
        return PersonaPromptConstants.DEFAULT_PERSONA;
    }

    /**
     * 获取会话的角色名称
     * @param session WebSocket会话
     * @return 角色名称
     */
    private String getCharacterName(WebSocketSession session) {
        // 从会话属性中获取角色名称
        Object characterName = session.getAttributes().get(SessionAttributeKeys.CHARACTER_NAME);
        if (characterName instanceof String nameString) {
            return nameString;
        }

        // 默认角色名称
        return PersonaPromptConstants.DEFAULT_CHARACTER_NAME;
    }

    /**
     * 获取或创建会话历史（从 Redis）
     * @param sessionId 会话ID
     * @return 会话历史列表（RList，支持分布式存储）
     */
    private RList<String> getSessionHistory(String sessionId) {
        String redisKey = RedisKeyConstants.SESSION_HISTORY_PREFIX + sessionId;
        return redissonClient.getList(redisKey);
    }

    /**
     * 获取会话锁（从 Redis）
     * @param sessionId 会话ID
     * @return 分布式锁（RLock）
     */
    private RLock getSessionLock(String sessionId) {
        String redisKey = RedisKeyConstants.SESSION_LOCK_PREFIX + sessionId;
        return redissonClient.getLock(redisKey);
    }

    /**
     * 发送音频响应
     * @param session WebSocket会话
     * @param audioData 音频数据
     */
    private void sendAudioResponse(WebSocketSession session, ByteBuffer audioData) {
        try {
            if (session.isOpen()) {
                BinaryMessage response = new BinaryMessage(audioData);
                session.sendMessage(response);
                logger.trace("向会话 {} 发送音频响应，大小: {} bytes",
                        session.getId(), audioData.remaining());
            } else {
                logger.warn("会话 {} 已关闭，无法发送音频响应", session.getId());
            }
        } catch (IOException e) {
            logger.error("向会话 {} 发送音频响应失败", session.getId(), e);
        }
    }

    /**
     * 发送错误消息
     * @param session WebSocket会话
     * @param errorMessage 错误消息
     */
    private void sendErrorMessage(WebSocketSession session, String errorMessage) {
        try {
            if (session.isOpen()) {
                // 使用安全的 WebSocketMessageDTO 进行序列化，防止 JSON 注入攻击
                WebSocketMessageDTO messageDTO = messageFactory.createError(
                    errorMessage, session.getId());
                String jsonMessage = objectMapper.writeValueAsString(messageDTO);
                TextMessage message = new TextMessage(jsonMessage);
                session.sendMessage(message);
                logger.debug("向会话 {} 发送错误消息: {}", session.getId(), errorMessage);
            }
        } catch (IOException e) {
            logger.error("向会话 {} 发送错误消息失败", session.getId(), e);
        } catch (Exception e) {
            logger.error("向会话 {} 序列化错误消息时发生异常", session.getId(), e);
        }
    }

    /**
     * 发送 TTS 错误通知到前端
     * @param session WebSocket 会话
     * @param errorMessage 错误消息
     */
    private void sendTtsErrorMessage(WebSocketSession session, String errorMessage) {
        try {
            if (session != null && session.isOpen()) {
                WebSocketMessageDTO messageDTO = messageFactory.createError(
                    errorMessage, session.getId());
                String jsonMessage = objectMapper.writeValueAsString(messageDTO);
                session.sendMessage(new TextMessage(jsonMessage));
                logger.debug("已向会话 {} 发送 TTS 错误通知", session.getId());
            }
        } catch (Exception e) {
            logger.error("向会话 {} 发送 TTS 错误通知失败", session.getId(), e);
        }
    }

    /**
     * 发送用户转写文本回显
     * @param session WebSocket会话
     * @param transcribedText 转写文本
     */
    private void sendUserTranscriptionEcho(WebSocketSession session, String transcribedText) {
        try {
            if (session.isOpen()) {
                // 使用安全的 WebSocketMessageDTO 进行序列化，防止 JSON 注入攻击
                WebSocketMessageDTO messageDTO = messageFactory.createUserTranscription(
                    transcribedText, session.getId());
                String jsonMessage = objectMapper.writeValueAsString(messageDTO);
                TextMessage message = new TextMessage(jsonMessage);
                session.sendMessage(message);
                logger.debug("向会话 {} 发送用户转写回显: {}", session.getId(), transcribedText);
            }
        } catch (IOException e) {
            logger.error("向会话 {} 发送用户转写回显失败", session.getId(), e);
        } catch (Exception e) {
            logger.error("向会话 {} 序列化用户转写消息时发生异常", session.getId(), e);
        }
    }

    /**
     * 克隆音频缓冲区
     * @param original 原始缓冲区
     * @return 克隆后的缓冲区
     */
    private ByteBuffer cloneAudioBuffer(ByteBuffer original) {
        if (original == null) {
            return null;
        }
        ByteBuffer clone = ByteBuffer.allocate(original.remaining());
        clone.put(original.duplicate());
        clone.flip();
        return clone;
    }

    /**
     * 音频缓冲区类
     * 用于存储会话的音频数据流
     */
    private static class AudioBuffer {
        private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        private ScheduledFuture<?> silenceDetectionTask;

        /**
         * 追加音频数据
         * @param audioChunk 音频数据块
         */
        public void append(ByteBuffer audioChunk) {
            byte[] data = new byte[audioChunk.remaining()];
            audioChunk.get(data);
            try {
                buffer.write(data);
            } catch (IOException e) {
                // ByteArrayOutputStream 不会抛出 IOException
                throw new RuntimeException(e);
            }
        }

        /**
         * 获取缓冲区大小
         * @return 字节数
         */
        public int size() {
            return buffer.size();
        }

        /**
         * 转换为字节数组
         * @return 音频数据
         */
        public byte[] toByteArray() {
            return buffer.toByteArray();
        }

        /**
         * 清空缓冲区
         */
        public void clear() {
            buffer.reset();
        }
    }

    /**
     * 检查文本是否仅包含标点符号
     * <p>
     * 用于过滤 ASR 幻觉（如静音被识别为"。"）
     * </p>
     *
     * @param text 待检查文本
     * @return 如果文本为空、长度<2 且仅包含标点符号，返回 true
     */
    private boolean isPunctuationOnly(String text) {
        if (text == null || text.isEmpty()) {
            return true;
        }
        // 正则：仅匹配中文/英文标点符号
        return text.matches("^[\\p{P}\\p{S}]+$");
    }
}