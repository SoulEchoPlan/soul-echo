package com.dotlinea.soulecho.exception;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 错误码枚举
 * <p>
 * 统一管理所有业务错误码，格式: [模块2位][类型1位][序号2位]
 * <ul>
 *   <li>模块: 10=通用，20=角色，30=知识库，40=聊天</li>
 *   <li>类型: 0=参数，1=业务，2=资源，3=系统</li>
 *   <li>序号: 01-99</li>
 * </ul>
 * </p>
 *
 * @author fanfan187
 * @version v1.0.0
 * @since v1.0.0
 */
@Getter
@AllArgsConstructor
public enum ErrorCode {

    // ==================== 通用错误 (10xxx) ====================

    /**
     * 操作成功
     */
    SUCCESS(0, "操作成功"),

    /**
     * 参数校验失败
     */
    PARAM_INVALID(10001, "参数校验失败"),

    /**
     * 必填参数缺失
     */
    PARAM_MISSING(10002, "必填参数缺失"),

    /**
     * 系统内部错误
     */
    SYSTEM_ERROR(10301, "系统内部错误"),

    /**
     * 系统繁忙
     */
    SYSTEM_BUSY(10302, "系统繁忙，请稍后重试"),

    // ==================== 角色模块 (20xxx) ====================

    /**
     * 角色不存在
     */
    CHARACTER_NOT_FOUND(20201, "角色不存在"),

    /**
     * 角色名称已存在
     */
    CHARACTER_NAME_DUPLICATE(20101, "角色名称已存在"),

    /**
     * 创建角色失败
     */
    CHARACTER_CREATE_FAILED(20102, "创建角色失败"),

    /**
     * 更新角色失败
     */
    CHARACTER_UPDATE_FAILED(20103, "更新角色失败"),

    /**
     * 删除角色失败
     */
    CHARACTER_DELETE_FAILED(20104, "删除角色失败"),

    /**
     * 查询角色失败
     */
    CHARACTER_QUERY_FAILED(20105, "查询角色失败"),

    // ==================== 知识库模块 (30xxx) ====================

    /**
     * 知识库文档不存在
     */
    KNOWLEDGE_NOT_FOUND(30201, "知识库文档不存在"),

    /**
     * 文档上传失败
     */
    KNOWLEDGE_UPLOAD_FAILED(30101, "文档上传失败"),

    /**
     * 知识检索失败
     */
    KNOWLEDGE_SEARCH_FAILED(30102, "知识检索失败"),

    /**
     * 文档删除失败
     */
    KNOWLEDGE_DELETE_FAILED(30103, "文档删除失败"),

    /**
     * 上传文件不能为空
     */
    KNOWLEDGE_FILE_EMPTY(30001, "上传文件不能为空"),

    /**
     * 文件大小超过限制
     */
    KNOWLEDGE_FILE_TOO_LARGE(30002, "文件大小超过限制"),

    // ==================== 聊天模块 (40xxx) ====================

    /**
     * 消息内容不能为空
     */
    CHAT_MESSAGE_EMPTY(40001, "消息内容不能为空"),

    /**
     * 会话正在处理中
     */
    CHAT_SESSION_BUSY(40101, "会话正在处理中，请稍后"),

    /**
     * AI 服务暂时不可用
     */
    CHAT_LLM_ERROR(40301, "AI 服务暂时不可用"),

    /**
     * 语音识别服务异常
     */
    CHAT_ASR_ERROR(40302, "语音识别服务异常"),

    /**
     * 语音合成服务异常
     */
    CHAT_TTS_ERROR(40303, "语音合成服务异常");

    /**
     * 错误码
     */
    private final int code;

    /**
     * 错误消息
     */
    private final String message;
}