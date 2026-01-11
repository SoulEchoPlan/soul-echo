package com.dotlinea.soulecho.client.impl;

import com.alibaba.nls.client.protocol.NlsClient;
import com.alibaba.nls.client.protocol.OutputFormatEnum;
import com.alibaba.nls.client.protocol.SampleRateEnum;
import com.alibaba.nls.client.protocol.tts.SpeechSynthesizer;
import com.alibaba.nls.client.protocol.tts.SpeechSynthesizerListener;
import com.alibaba.nls.client.protocol.tts.SpeechSynthesizerResponse;
import com.dotlinea.soulecho.client.TTSClient;
import com.dotlinea.soulecho.client.TTSTokenManager;
import com.dotlinea.soulecho.exception.TTSException;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * 阿里云流式语音合成 (TTS) 客户端实现
 * <p>
 * 通过阿里云智能语音交互 (NLS) SDK 实现流式文本到语音转换功能
 * </p>
 * <p>
 * 错误处理策略：
 * <ul>
 * <li>Token失效（418, 41020001）：刷新Token并重试1次</li>
 * <li>参数错误（如Voice ID为空）：直接熔断，不重试</li>
 * <li>其他错误（认证失败、服务端错误等）：直接熔断，不重试</li>
 * </ul>
 * </p>
 *
 * @author fanfan187
 * @version v1.0.0
 * @since v1.0.0
 */
@Component
@RequiredArgsConstructor
public class TTSClientImpl implements TTSClient {

    private static final Logger logger = LoggerFactory.getLogger(TTSClientImpl.class);

    private final TTSTokenManager tokenManager;

    @Value("${tts.service.url}")
    private String ttsServiceUrl;

    @Value("${tts.app.key}")
    private String appKey;

    @Value("${tts.voice:xiaoyun}")
    private String voice;

    @Value("${tts.format:pcm}")
    private String format;

    @Value("${tts.sample.rate:16000}")
    private Integer sampleRate;

    @Value("${tts.speech.rate:100}")
    private Integer speechRate;

    @Value("${tts.pitch.rate:0}")
    private Integer pitchRate;

    /**
     * Token失效错误码：需要刷新Token并重试
     */
    private static final int TOKEN_EXPIRED_CODE_1 = 418;
    private static final int TOKEN_EXPIRED_CODE_2 = 41020001;

    /**
     * 最大重试次数：Token失效时最多重试1次
     */
    private static final int MAX_RETRY_COUNT = 1;

    @Override
    public void synthesize(String text, Consumer<ByteBuffer> audioChunkConsumer) {
        if (text == null || text.trim().isEmpty()) {
            logger.warn("接收到空的文本内容");
            return;
        }

        if (audioChunkConsumer == null) {
            logger.warn("音频数据消费者为空");
            return;
        }

        // 执行TTS合成（支持Token失效重试）
        synthesizeWithRetry(text, audioChunkConsumer, 0);
    }

    /**
     * 带重试机制的TTS合成
     * <p>
     * 重试策略：
     * <ul>
     * <li>只有错误码为Token失效（418, 41020001）时才重试</li>
     * <li>参数错误（如Voice ID为空）直接熔断，不重试</li>
     * <li>其他错误（认证失败、服务端错误等）直接熔断，不重试</li>
     * <li>最多重试1次，避免无限重试</li>
     * </ul>
     * </p>
     *
     * @param text 待合成文本
     * @param audioChunkConsumer 音频数据消费者
     * @param retryCount 当前重试次数
     * @throws TTSException TTS合成失败（非Token失效错误或重试后仍失败）
     */
    private void synthesizeWithRetry(String text, Consumer<ByteBuffer> audioChunkConsumer, int retryCount) {
        // 用于记录TTS失败状态和错误信息
        AtomicBoolean ttsFailed = new AtomicBoolean(false);
        AtomicReference<Integer> statusCode = new AtomicReference<>(null);
        AtomicReference<String> statusText = new AtomicReference<>(null);

        SpeechSynthesizer synthesizer = null;

        try {
            // 获取有效Token（如果即将过期会自动刷新）
            String validToken = tokenManager.getValidToken();

            // 创建NLS客户端（每次使用最新Token）
            NlsClient nlsClient;
            if (ttsServiceUrl != null && !ttsServiceUrl.trim().isEmpty()) {
                nlsClient = new NlsClient(ttsServiceUrl, validToken);
            } else {
                nlsClient = new NlsClient(validToken);
            }

            // 创建语音合成器
            synthesizer = new SpeechSynthesizer(nlsClient, getSynthesizerListener(audioChunkConsumer, ttsFailed, statusCode, statusText));

            // 设置合成参数
            synthesizer.setAppKey(appKey);
            synthesizer.setText(text);
            synthesizer.setVoice(voice);
            synthesizer.setFormat(parseOutputFormat(format));
            synthesizer.setSampleRate(parseSampleRate(sampleRate));
            synthesizer.setVolume(50);  // 音量 0-100
            synthesizer.setSpeechRate(speechRate);  // 语速 -500 到 500
            synthesizer.setPitchRate(pitchRate);  // 语调 -500 到 500

            logger.debug("开始语音合成任务，文本长度: {} 字符", text.length());

            // 启动合成任务
            synthesizer.start();

            // 等待合成完成 (阻塞)
            synthesizer.waitForComplete();

            logger.info("语音合成任务完成，文本: {}", text);

        } catch (Exception e) {
            logger.error("语音合成过程中发生异常", e);

            // 如果是TTSException，根据错误码判断是否需要重试
            if (e instanceof TTSException ttsEx) {
                handleTTSException(ttsEx, text, audioChunkConsumer, retryCount);
                return;
            }

            // 根据listener记录的错误信息抛出TTSException
            if (ttsFailed.get()) {
                TTSException ttsEx = new TTSException(
                    "语音合成失败: " + statusText.get(),
                    statusCode.get() != null ? statusCode.get() : 500,
                    statusText.get() != null ? statusText.get() : e.getClass().getSimpleName(),
                    e
                );
                handleTTSException(ttsEx, text, audioChunkConsumer, retryCount);
                return;
            }

            // 其他未预期的异常，直接抛出（不重试）
            throw new TTSException(
                "语音合成失败: " + e.getMessage(),
                500,
                e.getClass().getSimpleName(),
                e
            );
        } finally {
            // 检查TTS是否失败，如果失败则抛出异常让上层处理
            if (ttsFailed.get()) {
                TTSException ttsEx = new TTSException(
                    "语音合成失败: " + statusText.get(),
                    statusCode.get() != null ? statusCode.get() : 500,
                    statusText.get() != null ? statusText.get() : "UNKNOWN_ERROR"
                );
                handleTTSException(ttsEx, text, audioChunkConsumer, retryCount);
                return;
            }

            // 清理资源
            if (synthesizer != null) {
                try {
                    synthesizer.close();
                } catch (Exception e) {
                    logger.warn("关闭 SpeechSynthesizer 时发生异常", e);
                }
            }
        }
    }

    /**
     * 处理TTS异常，根据错误码判断是否需要重试
     * <p>
     * 重试条件：
     * <ul>
     * <li>错误码为Token失效（418, 41020001）</li>
     * <li>当前重试次数 < MAX_RETRY_COUNT</li>
     * </ul>
     * </p>
     *
     * @param ttsEx TTS异常
     * @param text 待合成文本
     * @param audioChunkConsumer 音频数据消费者
     * @param retryCount 当前重试次数
     * @throws TTSException 不需要重试或重试后仍失败
     */
    private void handleTTSException(TTSException ttsEx, String text,
                                    Consumer<ByteBuffer> audioChunkConsumer, int retryCount) {
        int statusCode = ttsEx.getStatusCode();

        // 判断是否为Token失效错误
        boolean isTokenExpired = (statusCode == TOKEN_EXPIRED_CODE_1 ||
                                  statusCode == TOKEN_EXPIRED_CODE_2 ||
                                  ttsEx.isTokenExpired());

        if (isTokenExpired && retryCount < MAX_RETRY_COUNT) {
            // Token失效，刷新Token并重试
            logger.warn("检测到Token失效（错误码: {}），刷新Token并重试（第{}次）",
                statusCode, retryCount + 1);

            try {
                // 强制刷新Token
                tokenManager.forceRefresh();

                // 重试
                synthesizeWithRetry(text, audioChunkConsumer, retryCount + 1);
            } catch (Exception retryEx) {
                logger.error("Token刷新后重试失败", retryEx);
                throw ttsEx;  // 重试失败，抛出原始异常
            }
        } else if (isTokenExpired) {
            // Token失效但已达到最大重试次数
            logger.error("Token失效且已达到最大重试次数（{}），停止重试", MAX_RETRY_COUNT);
            throw ttsEx;
        } else {
            // 非Token失效错误，直接熔断
            logger.error("非Token失效错误（错误码: {}），直接熔断", statusCode);
            throw ttsEx;
        }
    }

    /**
     * 创建语音合成监听器
     * @param audioChunkConsumer 音频数据消费者
     * @param ttsFailed 失败标志（AtomicBoolean）
     * @param statusCode 错误码（AtomicReference）
     * @param statusText 错误文本（AtomicReference）
     * @return 合成监听器
     */
    private SpeechSynthesizerListener getSynthesizerListener(
            Consumer<ByteBuffer> audioChunkConsumer,
            AtomicBoolean ttsFailed,
            AtomicReference<Integer> statusCode,
            AtomicReference<String> statusText) {

        // 创建音频缓冲区，用于缓存完整的音频数据
        ByteArrayOutputStream audioBuffer = new ByteArrayOutputStream();

        return new SpeechSynthesizerListener() {
            @Override
            public void onComplete(SpeechSynthesizerResponse response) {
                logger.debug("语音合成完成，TaskId: {}", response.getTaskId());

                // 将缓存的完整音频数据一次性发送给前端
                byte[] completeAudio = audioBuffer.toByteArray();
                if (completeAudio.length > 0) {
                    logger.info("发送完整音频数据，大小: {} bytes", completeAudio.length);
                    audioChunkConsumer.accept(ByteBuffer.wrap(completeAudio));
                } else {
                    logger.warn("音频数据为空，未发送");
                }
            }

            @Override
            public void onMessage(ByteBuffer message) {
                // 收到音频数据块，写入缓冲区（不立即发送）
                if (message != null && message.hasRemaining()) {
                    logger.trace("收到音频数据块，大小: {} bytes", message.remaining());

                    try {
                        // 将ByteBuffer中的数据写入缓冲区
                        byte[] chunk = new byte[message.remaining()];
                        message.get(chunk);
                        audioBuffer.write(chunk);
                    } catch (Exception e) {
                        logger.error("写入音频缓冲区时发生异常", e);
                        ttsFailed.set(true);
                        statusCode.set(500);
                        statusText.set("音频缓冲区写入失败: " + e.getMessage());
                    }
                }
            }

            @Override
            public void onFail(SpeechSynthesizerResponse response) {
                // 合成失败，记录失败状态和错误信息
                int code = response.getStatus();
                String text = response.getStatusText();

                String errorMsg = String.format("语音合成失败: %s (状态码: %d)", text, code);
                logger.error(errorMsg);

                // 设置失败标志，让上层 finally 块检测到
                ttsFailed.set(true);
                statusCode.set(code);
                statusText.set(text);
            }
        };
    }

    /**
     * 解析输出格式
     * @param formatStr 格式字符串
     * @return 输出格式枚举
     */
    private OutputFormatEnum parseOutputFormat(String formatStr) {
        if (formatStr == null) {
            return OutputFormatEnum.PCM;
        }
        return switch (formatStr.toLowerCase()) {
            case "wav" -> OutputFormatEnum.WAV;
            case "mp3" -> OutputFormatEnum.MP3;
            default -> OutputFormatEnum.PCM;
        };
    }

    /**
     * 解析采样率
     * @param rate 采样率数值
     * @return 采样率枚举
     */
    private SampleRateEnum parseSampleRate(Integer rate) {
        if (rate == null) {
            return SampleRateEnum.SAMPLE_RATE_16K;
        }
        return switch (rate) {
            case 8000 -> SampleRateEnum.SAMPLE_RATE_8K;
            default -> SampleRateEnum.SAMPLE_RATE_16K;
        };
    }
}