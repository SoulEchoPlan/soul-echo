package com.dotlinea.soulecho.config;

import com.dotlinea.soulecho.controller.ChatWebSocketHandler;
import lombok.AllArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.server.standard.ServletServerContainerFactoryBean;

/**
 * WebSocket 配置类
 * <p>
 * 该类用于配置 WebSocket，并注册 WebSocket 处理器。
 * </p>
 *
 * @author fanfan187
 * @version v1.0.0
 * @since v1.0.0
 */
@Configuration
@EnableWebSocket
@AllArgsConstructor
public class WebSocketConfig implements WebSocketConfigurer {

    private final ChatWebSocketHandler chatWebSocketHandler;

    /**
     * 注册WebSocket处理器
     * <p>
     * 该方法将ChatWebSocketHandler注册到"/chat"路径，并允许所有来源的跨域请求
     * </p>
     *
     * @param registry WebSocket处理器注册中心
     */
    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(chatWebSocketHandler, "/chat")
                .setAllowedOrigins("*");
    }

    /**
     * 配置 WebSocket 容器，解决 1009 错误
     * 此配置会增大服务器处理二进制和文本消息的缓冲区
     */
    @Bean
    public ServletServerContainerFactoryBean createWebSocketContainer() {
        ServletServerContainerFactoryBean container = new ServletServerContainerFactoryBean();
        // 设置最大文本消息缓冲区大小，例如 512KB
        container.setMaxTextMessageBufferSize(512 * 1024);
        // 设置最大二进制消息缓冲区大小，例如 512KB
        container.setMaxBinaryMessageBufferSize(512 * 1024);
        // 可选：设置会话空闲超时时间（毫秒）
        container.setMaxSessionIdleTimeout(30 * 60 * 1000L); // 30分钟
        return container;
    }
}