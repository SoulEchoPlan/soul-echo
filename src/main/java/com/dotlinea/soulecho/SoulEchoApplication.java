package com.dotlinea.soulecho;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Soul Echo 应用程序主启动类
 * dotlinea团队 - Soul Echo Backend
 *
 * 功能特性：
 * - 基于Spring Boot 3+ 的现代化后端架构
 * - 支持RESTful API的AI角色管理
 * - 基于WebSocket的实时语音/文本对话
 * - 集成ASR、LLM、TTS等AI能力
 * - MySQL数据库存储角色信息
 */
@SpringBootApplication
public class SoulEchoApplication {

    /**
     * 应用程序入口点
     * @param args 命令行参数
     */
    public static void main(String[] args) {
        SpringApplication.run(SoulEchoApplication.class, args);
        System.out.println("Hello");
    }
}