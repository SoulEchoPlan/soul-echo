package com.dotlinea.soulecho.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.lang.reflect.Method;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 异步任务配置类
 * <p>
 * 为 Spring 的 @Async 注解提供统一的线程池配置，支持事件驱动和异步任务执行。
 * 采用合理的线程池参数，避免资源耗尽，并提供完善的异常处理机制。
 * </p>
 *
 * @author fanfan187
 * @version v1.0.0
 * @since v1.0.0
 */
@Slf4j
@Configuration
@EnableAsync
public class AsyncConfig implements AsyncConfigurer {

    /**
     * 配置默认的异步任务执行器
     * <p>
     * 线程池参数说明：
     * - 核心线程数：10（足够处理日常并发）
     * - 最大线程数：50（高峰期可扩展）
     * - 队列容量：200（缓冲突发请求）
     * - 拒绝策略：CallerRunsPolicy（降级到调用线程执行，避免任务丢失）
     * </p>
     *
     * @return 异步任务执行器
     */
    @Bean(name = "taskExecutor")
    @Override
    public Executor getAsyncExecutor() {
        var executor = new ThreadPoolTaskExecutor();

        // 核心线程数（常驻线程）
        executor.setCorePoolSize(10);

        // 最大线程数（高峰期可扩展）
        executor.setMaxPoolSize(50);

        // 队列容量（缓冲等待任务）
        executor.setQueueCapacity(200);

        // 线程名前缀（便于日志追踪）
        executor.setThreadNamePrefix("async-task-");

        // 线程空闲时间（秒）- 超过核心线程数的线程在空闲60秒后回收
        executor.setKeepAliveSeconds(60);

        // 拒绝策略：当队列满且线程池已达最大线程数时，由调用线程执行
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());

        // 等待所有任务完成后再关闭线程池（优雅关闭）
        executor.setWaitForTasksToCompleteOnShutdown(true);

        // 设置最大等待时间（秒）
        executor.setAwaitTerminationSeconds(60);

        executor.initialize();

        log.info("异步线程池初始化完成 - CorePoolSize: {}, MaxPoolSize: {}, QueueCapacity: {}",
                executor.getCorePoolSize(), executor.getMaxPoolSize(), executor.getQueueCapacity());

        return executor;
    }

    /**
     * 配置知识库上传专用的异步任务执行器
     * <p>
     * 由于知识库上传涉及大文件上传和索引构建，这些任务耗时较长，
     * 使用独立的线程池可以避免影响其他业务的异步任务执行。
     * </p>
     *
     * @return 知识库上传专用执行器
     */
    @Bean(name = "knowledgeUploadExecutor")
    public Executor knowledgeUploadExecutor() {
        var executor = new ThreadPoolTaskExecutor();

        // 知识库上传通常是低频高耗时任务，核心线程数可以较少
        executor.setCorePoolSize(3);

        // 最大线程数设置较小，避免并发上传过多导致带宽和内存压力
        executor.setMaxPoolSize(10);

        // 队列容量较大，允许排队上传
        executor.setQueueCapacity(100);

        executor.setThreadNamePrefix("knowledge-upload-");
        executor.setKeepAliveSeconds(120);

        // 拒绝策略：队列满时，由调用线程执行（会阻塞上传请求，提供背压）
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());

        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(180);

        executor.initialize();

        log.info("知识库上传线程池初始化完成 - CorePoolSize: {}, MaxPoolSize: {}, QueueCapacity: {}",
                executor.getCorePoolSize(), executor.getMaxPoolSize(), executor.getQueueCapacity());

        return executor;
    }

    /**
     * 配置实时聊天服务专用的异步任务执行器
     * <p>
     * 用于处理 WebSocket 会话的并发消息，包括 ASR、LLM、TTS 等计算密集型任务。
     * 采用较高的最大线程数以应对高并发聊天场景。
     * </p>
     *
     * @return 实时聊天专用执行器
     */
    @Bean(name = "chatExecutor")
    public Executor chatExecutor() {
        var executor = new ThreadPoolTaskExecutor();

        // 核心线程数：保持较小的核心线程数以节省资源
        executor.setCorePoolSize(10);

        // 最大线程数：支持高并发聊天场景
        executor.setMaxPoolSize(200);

        // 队列容量：有限队列避免内存溢出
        executor.setQueueCapacity(50);

        executor.setThreadNamePrefix("chat-exec-");
        executor.setKeepAliveSeconds(60);

        // 拒绝策略：由调用线程执行，防止消息丢失
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());

        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);

        executor.initialize();

        log.info("实时聊天线程池初始化完成 - CorePoolSize: {}, MaxPoolSize: {}, QueueCapacity: {}",
                executor.getCorePoolSize(), executor.getMaxPoolSize(), executor.getQueueCapacity());

        return executor;
    }

    /**
     * 配置异步任务的全局异常处理器
     * <p>
     * 捕获 @Async 方法中未被捕获的异常，记录详细日志，避免异常被吞掉。
     * </p>
     *
     * @return 异常处理器
     */
    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return new CustomAsyncExceptionHandler();
    }

    /**
     * 自定义异步异常处理器
     */
    @Slf4j
    private static class CustomAsyncExceptionHandler implements AsyncUncaughtExceptionHandler {
        @Override
        public void handleUncaughtException(Throwable throwable, Method method, Object... params) {
            log.error("异步任务执行失败 - 方法: {}.{}, 参数: {}, 异常: {}",
                    method.getDeclaringClass().getSimpleName(),
                    method.getName(),
                    params,
                    throwable.getMessage(),
                    throwable);
        }
    }
}