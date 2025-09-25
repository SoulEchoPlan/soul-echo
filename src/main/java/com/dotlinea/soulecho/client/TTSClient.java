package com.dotlinea.soulecho.client;

import java.nio.ByteBuffer;

/**
 * 文本转语音 (TTS) 服务客户端接口
 *
 * @author fanfan187
 * @version v1.0.0
 * @since v1.0.0
 */
public interface TTSClient {

    /**
     * 将文本合成为语音
     * @param text 要合成的文本
     * @return 包含音频数据的 ByteBuffer
     */
    ByteBuffer synthesize(String text);
}