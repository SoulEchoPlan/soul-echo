//package com.dotlinea.soulecho.service;
//
//import org.springframework.web.socket.BinaryMessage;
//import org.springframework.web.socket.WebSocketSession;
//
///**
// * 实时聊天服务接口
// * <p>
// * 处理WebSocket连接中的音频消息和文本回复
// * </p>
// *
// * @author fanfan187
// * @version v1.0.0
// * @since v1.0.0
// */
//public interface RealtimeChatService {
//
//    /**
//     * 处理音频消息
//     * @param session WebSocket会话
//     * @param message 音频消息
//     */
//    void handleBinaryMessage(WebSocketSession session, BinaryMessage message);
//
//    /**
//     * 处理文本消息
//     * @param personaPrompt 角色设定
//     * @param userInput 用户输入
//     * @param sessionId 会话ID
//     * @return LLM回复
//     */
//    String processTextChat(String personaPrompt, String userInput, String sessionId);
//
//    /**
//     * 清理会话
//     * @param sessionId 会话ID
//     */
//    void cleanupSession(String sessionId);
//}