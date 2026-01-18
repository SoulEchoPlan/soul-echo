package com.dotlinea.soulecho.config;

import com.aliyun.bailian20231229.Client;
import com.aliyun.teaopenapi.models.Config;
import okhttp3.OkHttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

/**
 * 阿里云百炼服务配置类
 * <p>
 * 提供百炼 Client 和 OkHttpClient Bean，供知识库服务和监听器使用
 * </p>
 *
 * @author fanfan187
 * @version v1.0.0
 * @since v1.0.0
 */
@Configuration
public class BailianConfig {

    private static final Logger logger = LoggerFactory.getLogger(BailianConfig.class);

    @Value("${bailian.accessKeyId}")
    private String accessKeyId;

    @Value("${bailian.accessKeySecret}")
    private String accessKeySecret;

    @Value("${bailian.endpoint:bailian.cn-beijing.aliyuncs.com}")
    private String endpoint;

    @Value("${bailian.workspace.id}")
    private String workspaceId;

    @Value("${bailian.knowledge.index.id}")
    private String indexId;

    /**
     * 创建阿里云百炼 Client Bean
     *
     * @return 百炼 Client 实例
     */
    @Bean
    public Client bailianClient() {
        try {
            logger.info("初始化阿里云百炼 Client...");

            Config config = new Config()
                    .setAccessKeyId(accessKeyId)
                    .setAccessKeySecret(accessKeySecret)
                    .setEndpoint(endpoint);

            Client client = new Client(config);
            logger.info("阿里云百炼 Client 初始化成功，Workspace: {}", workspaceId);
            return client;
        } catch (Exception e) {
            logger.error("阿里云百炼 Client 初始化失败", e);
            throw new RuntimeException("百炼 Client 初始化异常", e);
        }
    }

    /**
     * 创建 OkHttpClient Bean（用于文件上传）
     *
     * @return OkHttpClient 实例
     */
    @Bean
    public OkHttpClient httpClient() {
        logger.info("初始化 OkHttpClient...");
        return new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                .build();
    }
}