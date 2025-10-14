package com.dotlinea.soulecho.client;

import java.nio.ByteBuffer;
import java.util.function.Consumer;

/**
 * 文本转语音 (TTS) 服务客户端接口
 *
 * @author fanfan187
 * @version v1.0.0
 * @since v1.0.0
 */
public interface TTSClient {

    /**
     * 将文本流式合成为语音，通过回调函数实时返回音频数据块
     * @param text 要合成的文本
     * @param audioChunkConsumer 音频数据块，每收到一块音频数据就会被调用
     */
    void synthesize(String text, Consumer<ByteBuffer> audioChunkConsumer);
}