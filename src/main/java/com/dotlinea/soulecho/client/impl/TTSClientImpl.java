package com.dotlinea.soulecho.client.impl;

import com.alibaba.nls.client.AccessToken;
import com.alibaba.nls.client.protocol.NlsClient;
import com.alibaba.nls.client.protocol.OutputFormatEnum;
import com.alibaba.nls.client.protocol.SampleRateEnum;
import com.alibaba.nls.client.protocol.tts.SpeechSynthesizer;
import com.alibaba.nls.client.protocol.tts.SpeechSynthesizerListener;
import com.alibaba.nls.client.protocol.tts.SpeechSynthesizerResponse;
import com.dotlinea.soulecho.client.TTSClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.nio.ByteBuffer;
import java.util.function.Consumer;

/**
 * 阿里云流式语音合成 (TTS) 客户端实现
 * <p>
 * 通过阿里云智能语音交互 (NLS) SDK 实现流式文本到语音转换功能
 * </p>
 *
 * @author fanfan187
 * @version v1.0.0
 * @since v1.0.0
 */
@Component
public class TTSClientImpl implements TTSClient {

    private static final Logger logger = LoggerFactory.getLogger(TTSClientImpl.class);

    @Value("${tts.service.url}")
    private String ttsServiceUrl;

    @Value("${tts.api.key}")
    private String apiKey;

    @Value("${tts.api.secret}")
    private String apiSecret;

    @Value("${tts.app.key}")
    private String appKey;

    @Value("${tts.voice:xiaoyun}")
    private String voice;

    @Value("${tts.format:pcm}")
    private String format;

    @Value("${tts.sample.rate:16000}")
    private Integer sampleRate;

    private NlsClient nlsClient;

    @PostConstruct
    public void init() {
        try {
            // 使用 AccessKeyId 和 AccessKeySecret 获取 Token
            AccessToken accessToken = new AccessToken(apiKey, apiSecret);
            accessToken.apply();
            String token = accessToken.getToken();

            logger.info("TTS Token获取成功，过期时间: {}", accessToken.getExpireTime());

            // 使用 Token 初始化 NLS 客户端
            // 如果配置了服务URL，使用指定URL；否则使用默认URL
            if (ttsServiceUrl != null && !ttsServiceUrl.trim().isEmpty()) {
                nlsClient = new NlsClient(ttsServiceUrl, token);
                logger.info("TTS 客户端初始化成功，ServiceURL: {}, AppKey: {}, Voice: {}, Format: {}, SampleRate: {}",
                        ttsServiceUrl, appKey, voice, format, sampleRate);
            } else {
                nlsClient = new NlsClient(token);
                logger.info("TTS 客户端初始化成功（使用默认URL），AppKey: {}, Voice: {}, Format: {}, SampleRate: {}",
                        appKey, voice, format, sampleRate);
            }
        } catch (Exception e) {
            logger.error("TTS 客户端初始化失败", e);
            throw new RuntimeException("TTS 客户端初始化失败", e);
        }
    }

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

        SpeechSynthesizer synthesizer = null;
        try {
            // 创建语音合成器
            synthesizer = new SpeechSynthesizer(nlsClient, getSynthesizerListener(audioChunkConsumer));

            // 设置合成参数
            synthesizer.setAppKey(appKey);
            synthesizer.setText(text);
            synthesizer.setVoice(voice);
            synthesizer.setFormat(parseOutputFormat(format));
            synthesizer.setSampleRate(parseSampleRate(sampleRate));
            synthesizer.setVolume(50);  // 音量 0-100
            synthesizer.setSpeechRate(0);  // 语速 -500 到 500
            synthesizer.setPitchRate(0);  // 语调 -500 到 500

            logger.debug("开始语音合成任务，文本长度: {} 字符", text.length());

            // 启动合成任务
            synthesizer.start();

            // 等待合成完成 (阻塞)
            synthesizer.waitForComplete();

            logger.info("语音合成任务完成，文本: {}", text);

        } catch (Exception e) {
            logger.error("语音合成过程中发生异常", e);
        } finally {
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
     * 创建语音合成监听器
     * @param audioChunkConsumer 音频数据消费者
     * @return 合成监听器
     */
    private SpeechSynthesizerListener getSynthesizerListener(Consumer<ByteBuffer> audioChunkConsumer) {
        return new SpeechSynthesizerListener() {
            @Override
            public void onComplete(SpeechSynthesizerResponse response) {
                logger.debug("语音合成完成，TaskId: {}", response.getTaskId());
            }

            @Override
            public void onMessage(ByteBuffer message) {
                // 收到音频数据块，立即通过回调传递出去
                if (message != null && message.hasRemaining()) {
                    logger.trace("收到音频数据块，大小: {} bytes", message.remaining());
                    audioChunkConsumer.accept(message);
                }
            }

            @Override
            public void onFail(SpeechSynthesizerResponse response) {
                // 合成失败
                String errorMessage = String.format("语音合成失败: %s (状态码: %d)",
                        response.getStatusText(), response.getStatus());
                logger.error(errorMessage);
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