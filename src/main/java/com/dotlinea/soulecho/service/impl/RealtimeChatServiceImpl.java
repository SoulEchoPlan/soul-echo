package com.dotlinea.soulecho.service.impl;

import com.dotlinea.soulecho.client.ASRClient;
import com.dotlinea.soulecho.client.LLMClient;
import com.dotlinea.soulecho.client.TTSClient;
import com.dotlinea.soulecho.dto.WebSocketMessageDTO;
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
     * 如果在此时间内没有收到新的音频数据，则认为用户说话结束
     */
    private static final long SILENCE_TIMEOUT_MS = 500;

    /**
     * Redis 键前缀 - 会话历史
     */
    private static final String REDIS_KEY_SESSION_HISTORY = "soul-echo:session:history:";

    /**
     * Redis 键前缀 - 会话锁
     */
    private static final String REDIS_KEY_SESSION_LOCK = "soul-echo:session:lock:";

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
        boolean lockAcquired = false;
        try {
            lockAcquired = sessionLock.tryLock(100, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            logger.warn("会话 {} 尝试获取锁时被中断", sessionId);
            Thread.currentThread().interrupt();
            sendErrorMessage(session, "系统繁忙，请稍后重试");
            return;
        }

        if (!lockAcquired) {
            logger.warn("会话 {} 正在处理消息，拒绝本次请求", sessionId);
            sendErrorMessage(session, "您的上一条消息正在处理中，请稍后再试");
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

                        logger.info("会话 {} 识别结果: {}", sessionId, recognizedText);

                        // === 步骤2: 发送用户转写文本回显 ===
                        sendUserTranscriptionEcho(session, recognizedText);

                        // === 步骤3: 获取角色设定 ===
                        String personaPrompt = getPersonaPrompt(session);
                        String characterName = getCharacterName(session);

                        // === 步骤4: LLM流式对话生成 + 句子级流式TTS ===
                        logger.debug("会话 {} 开始 LLM 流式对话生成", sessionId);
                        StringBuilder sentenceBuffer = new StringBuilder();

                        try {
                            processTextChatStream(personaPrompt, recognizedText, sessionId, characterName, chunk -> {
                                sentenceBuffer.append(chunk);

                                // 使用正则表达式提取完整句子
                                String bufferedText = sentenceBuffer.toString();
                                java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("[^.!?。！？]+[.!?。！？]");
                                java.util.regex.Matcher matcher = pattern.matcher(bufferedText);

                                int lastMatchEnd = 0;
                                while (matcher.find()) {
                                    String completeSentence = matcher.group();
                                    lastMatchEnd = matcher.end();

                                    logger.debug("会话 {} 提取完整句子: {}", sessionId, completeSentence);

                                    // 立即将完整句子发送给前端显示
                                    try {
                                        if (session.isOpen()) {
                                            session.sendMessage(new TextMessage(completeSentence));
                                            logger.trace("向会话 {} 发送句子文本: {}", sessionId, completeSentence);
                                        }
                                    } catch (IOException e) {
                                        logger.error("向会话 {} 发送句子文本失败", sessionId, e);
                                    }

                                    // 立即将完整句子送去TTS合成
                                    try {
                                        ttsClient.synthesize(completeSentence, audioChunk -> sendAudioResponse(session, audioChunk));
                                    } catch (Exception e) {
                                        logger.error("会话 {} TTS合成句子失败: {}", sessionId, completeSentence, e);
                                    }
                                }

                                // 移除已处理的句子，保留未完成的部分
                                if (lastMatchEnd > 0) {
                                    sentenceBuffer.delete(0, lastMatchEnd);
                                }
                            });

                            // 处理剩余的不成句内容
                            String remainingText = sentenceBuffer.toString().trim();
                            if (!remainingText.isEmpty()) {
                                logger.debug("会话 {} 处理剩余文本: {}", sessionId, remainingText);

                                // 发送剩余文本给前端
                                try {
                                    if (session.isOpen()) {
                                        session.sendMessage(new TextMessage(remainingText));
                                    }
                                } catch (IOException e) {
                                    logger.error("向会话 {} 发送剩余文本失败", sessionId, e);
                                }

                                // TTS合成剩余文本
                                try {
                                    ttsClient.synthesize(remainingText, audioChunk -> sendAudioResponse(session, audioChunk));
                                } catch (Exception e) {
                                    logger.error("会话 {} TTS合成剩余文本失败", sessionId, e);
                                }
                            }

                            logger.info("会话 {} 完整音频处理流程结束", sessionId);
                        } catch (Exception e) {
                            logger.error("会话 {} LLM流式对话处理失败", sessionId, e);
                            sendErrorMessage(session, "生成回复时发生错误，请稍后重试");
                        }
                    })
                    .exceptionally(throwable -> {
                        // 全链路异常处理
                        logger.error("会话 {} 异步处理音频消息时发生异常", sessionId, throwable);
                        sendErrorMessage(session, "处理您的消息时发生错误，请稍后重试。");
                        return null;
                    })
                    .whenComplete((result, throwable) -> {
                        // 确保在任何情况下都释放锁
                        if (sessionLock.isHeldByCurrentThread()) {
                            sessionLock.unlock();
                            logger.debug("会话 {} 释放分布式锁", sessionId);
                        }
                    });

        } catch (Exception e) {
            logger.error("会话 {} 启动异步处理时发生异常", sessionId, e);
            sendErrorMessage(session, "处理您的消息时发生错误，请稍后重试。");

            // 确保异常情况下释放锁
            if (sessionLock.isHeldByCurrentThread()) {
                sessionLock.unlock();
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
    public void cleanupSession(String sessionId) {
        logger.info("清理会话 {} 的资源", sessionId);

        // 清理 Redis 中的会话历史
        String historyKey = REDIS_KEY_SESSION_HISTORY + sessionId;
        RList<String> sessionHistory = redissonClient.getList(historyKey);
        sessionHistory.delete();
        logger.debug("已删除会话 {} 的 Redis 历史记录", sessionId);

        // 清理 Redis 中的会话锁（如果存在且未被占用）
        String lockKey = REDIS_KEY_SESSION_LOCK + sessionId;
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
        Object personaPrompt = session.getAttributes().get("personaPrompt");
        if (personaPrompt instanceof String promptString) {
            return promptString;
        }

        // 默认角色设定
        return "你是一个友好、有帮助的AI助手。请用自然、亲切的语气与用户对话。";
    }

    /**
     * 获取会话的角色名称
     * @param session WebSocket会话
     * @return 角色名称
     */
    private String getCharacterName(WebSocketSession session) {
        // 从会话属性中获取角色名称
        Object characterName = session.getAttributes().get("characterName");
        if (characterName instanceof String nameString) {
            return nameString;
        }

        // 默认角色名称
        return "default";
    }

    /**
     * 获取或创建会话历史（从 Redis）
     * @param sessionId 会话ID
     * @return 会话历史列表（RList，支持分布式存储）
     */
    private RList<String> getSessionHistory(String sessionId) {
        String redisKey = REDIS_KEY_SESSION_HISTORY + sessionId;
        return redissonClient.getList(redisKey);
    }

    /**
     * 获取会话锁（从 Redis）
     * @param sessionId 会话ID
     * @return 分布式锁（RLock）
     */
    private RLock getSessionLock(String sessionId) {
        String redisKey = REDIS_KEY_SESSION_LOCK + sessionId;
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
}