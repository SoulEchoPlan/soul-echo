package com.dotlinea.soulecho.client.impl;

import com.alibaba.nls.client.AccessToken;
import com.alibaba.nls.client.protocol.InputFormatEnum;
import com.alibaba.nls.client.protocol.NlsClient;
import com.alibaba.nls.client.protocol.SampleRateEnum;
import com.alibaba.nls.client.protocol.asr.SpeechTranscriber;
import com.alibaba.nls.client.protocol.asr.SpeechTranscriberListener;
import com.alibaba.nls.client.protocol.asr.SpeechTranscriberResponse;
import com.dotlinea.soulecho.client.ASRClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.io.InputStream;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * 阿里云实时语音识别 (ASR) 客户端实现
 * <p>
 * 通过阿里云智能语音交互 (NLS) SDK 实现完整的音频流识别功能
 * </p>
 *
 * @author fanfan187
 * @version v1.0.0
 * @since v1.0.0
 */
@Component
public class ASRClientImpl implements ASRClient {

    private static final Logger logger = LoggerFactory.getLogger(ASRClientImpl.class);

    /**
     * 每次发送的音频数据块大小 (3200字节，约100ms @ 16kHz)
     */
    private static final int CHUNK_SIZE = 3200;

    @Value("${asr.service.url}")
    private String asrServiceUrl;

    @Value("${asr.api.key}")
    private String apiKey;

    @Value("${asr.api.secret}")
    private String apiSecret;

    @Value("${asr.app.key}")
    private String appKey;

    private NlsClient nlsClient;

    @PostConstruct
    public void init() {
        try {
            // 使用 AccessKeyId 和 AccessKeySecret 获取 Token
            AccessToken accessToken = new AccessToken(apiKey, apiSecret);
            accessToken.apply();
            String token = accessToken.getToken();

            logger.info("ASR Token获取成功，过期时间: {}", accessToken.getExpireTime());

            // 使用 Token 初始化 NLS 客户端
            // 如果配置了服务URL，使用指定URL；否则使用默认URL
            if (asrServiceUrl != null && !asrServiceUrl.trim().isEmpty()) {
                nlsClient = new NlsClient(asrServiceUrl, token);
                logger.info("ASR 客户端初始化成功，ServiceURL: {}, AppKey: {}", asrServiceUrl, appKey);
            } else {
                nlsClient = new NlsClient(token);
                logger.info("ASR 客户端初始化成功（使用默认URL），AppKey: {}", appKey);
            }
        } catch (Exception e) {
            logger.error("ASR 客户端初始化失败", e);
            throw new RuntimeException("ASR 客户端初始化失败", e);
        }
    }

    @Override
    public String recognize(InputStream audioStream) {
        if (audioStream == null) {
            logger.warn("接收到空的音频流");
            return null;
        }

        SpeechTranscriber transcriber = null;
        try {
            // 创建异步结果容器
            CompletableFuture<String> resultFuture = new CompletableFuture<>();
            StringBuilder fullText = new StringBuilder();

            // 创建实时语音识别对象
            transcriber = new SpeechTranscriber(nlsClient, getTranscriberListener(resultFuture, fullText));

            // 设置识别参数
            transcriber.setAppKey(appKey);
            transcriber.setFormat(InputFormatEnum.PCM);
            transcriber.setSampleRate(SampleRateEnum.SAMPLE_RATE_16K);
            transcriber.setEnableIntermediateResult(true);

            logger.debug("开始实时语音识别任务");

            // 启动识别会话
            transcriber.start();

            // 从输入流循环读取音频数据并发送
            byte[] buffer = new byte[CHUNK_SIZE];
            int bytesRead;
            while ((bytesRead = audioStream.read(buffer)) != -1) {
                if (bytesRead > 0) {
                    // 发送音频数据到阿里云
                    byte[] audioData = new byte[bytesRead];
                    System.arraycopy(buffer, 0, audioData, 0, bytesRead);
                    transcriber.send(audioData);
                    logger.trace("发送音频数据块，大小: {} bytes", bytesRead);
                }
            }

            // 通知识别结束
            transcriber.stop();
            logger.debug("音频流发送完毕，等待识别结果");

            // 阻塞等待识别结果 (最长等待10秒)
            String result = resultFuture.get(10, TimeUnit.SECONDS);
            logger.info("语音识别成功: {}", result);
            return result;

        } catch (Exception e) {
            logger.error("语音识别过程中发生异常", e);
            return null;
        } finally {
            // 清理资源
            if (transcriber != null) {
                try {
                    transcriber.close();
                } catch (Exception e) {
                    logger.warn("关闭 SpeechTranscriber 时发生异常", e);
                }
            }
            // 关闭输入流
            try {
                audioStream.close();
            } catch (Exception e) {
                logger.warn("关闭音频流时发生异常", e);
            }
        }
    }

    /**
     * 创建语音识别监听器
     * @param resultFuture 用于传递识别结果的 Future
     * @param fullText 用于累积完整文本的 StringBuilder
     * @return 识别监听器
     */
    private SpeechTranscriberListener getTranscriberListener(
            CompletableFuture<String> resultFuture,
            StringBuilder fullText) {
        return new SpeechTranscriberListener() {
            @Override
            public void onTranscriberStart(SpeechTranscriberResponse response) {
                logger.debug("实时语音识别会话开始，TaskId: {}", response.getTaskId());
            }

            @Override
            public void onSentenceBegin(SpeechTranscriberResponse response) {
                logger.trace("句子开始: Time={}", response.getTransSentenceTime());
            }

            @Override
            public void onSentenceEnd(SpeechTranscriberResponse response) {
                // 句子结束，累积文本
                String sentenceText = response.getTransSentenceText();
                if (sentenceText != null && !sentenceText.isEmpty()) {
                    fullText.append(sentenceText);
                    logger.debug("句子识别结果: {}", sentenceText);
                }
            }

            @Override
            public void onTranscriptionResultChange(SpeechTranscriberResponse response) {
                // 中间结果变化，仅用于调试
                logger.trace("识别中间结果: {}", response.getTransSentenceText());
            }

            @Override
            public void onTranscriptionComplete(SpeechTranscriberResponse response) {
                // 识别完成，返回最终结果
                String finalText = fullText.toString();
                logger.debug("识别最终结果: {}", finalText);
                resultFuture.complete(finalText);
            }

            @Override
            public void onFail(SpeechTranscriberResponse response) {
                // 识别失败
                String errorMessage = String.format("语音识别失败: %s (状态码: %d)",
                        response.getStatusText(), response.getStatus());
                logger.error(errorMessage);
                resultFuture.completeExceptionally(new RuntimeException(errorMessage));
            }
        };
    }
}