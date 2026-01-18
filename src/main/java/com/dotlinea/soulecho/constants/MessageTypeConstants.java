package com.dotlinea.soulecho.constants;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * WebSocket 消息类型常量
 * <p>
 * 定义前后端 WebSocket 通信的所有消息类型标识
 * </p>
 *
 * @author fanfan187
 * @version v1.0.0
 * @since v1.0.0
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class MessageTypeConstants {

    /**
     * 应用层心跳 - Ping 消息
     */
    public static final String PING = "ping";

    /**
     * 应用层心跳 - Pong 响应
     */
    public static final String PONG = "pong";

    /**
     * 用户语音转写文本消息
     */
    public static final String USER_TRANSCRIPTION = "user-transcription";

    /**
     * 错误消息
     */
    public static final String ERROR = "error";

    /**
     * AI 回复消息
     */
    public static final String AI_REPLY = "ai-reply";

    /**
     * 音频信息消息
     */
    public static final String AUDIO_INFO = "audio-info";

    /**
     * 错误码 - TTS 服务已熔断
     */
    public static final String TTS_BROKEN = "TTS_BROKEN";
}