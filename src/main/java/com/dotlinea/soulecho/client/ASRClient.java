package com.dotlinea.soulecho.client;

import java.nio.ByteBuffer;

/**
 * 语音识别 (ASR) 服务客户端接口
 * <p>
 * 定义了与外部ASR服务交互的标准。
 * </p>
 *
 * @author fanfan187
 * @version v1.0.0
 * @since v1.0.0
 */
public interface ASRClient {

    /**
     * 发送音频数据块进行实时识别
     * @param audioChunk 音频数据
     * @return 识别出的文本结果，若无结果则返回null
     */
    String recognize(ByteBuffer audioChunk);

}