package com.dotlinea.soulecho.client;

import java.io.InputStream;
import java.util.concurrent.CompletableFuture;

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
     * 处理完整的音频流进行实时识别（异步）
     * @param audioStream 完整音频数据流
     * @return CompletableFuture包装的识别文本结果，若无结果则返回null
     */
    CompletableFuture<String> recognizeAsync(InputStream audioStream);

}