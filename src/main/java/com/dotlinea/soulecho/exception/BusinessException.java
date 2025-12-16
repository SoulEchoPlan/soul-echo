package com.dotlinea.soulecho.exception;

import lombok.Getter;

/**
 * 业务异常
 * <p>
 * 当业务逻辑出现错误时抛出此异常，支持错误码体系
 * </p>
 *
 * @author fanfan187
 * @version v1.0.0
 * @since v1.0.0
 */
@Getter
public class BusinessException extends RuntimeException {

    /**
     * 错误码
     */
    private final int errorCode;

    /**
     * 使用 ErrorCode 枚举构造
     *
     * @param errorCode 错误码枚举
     */
    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode.getCode();
    }

    /**
     * 使用 ErrorCode 枚举 + 自定义消息构造
     *
     * @param errorCode 错误码枚举
     * @param message   自定义错误消息
     */
    public BusinessException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode.getCode();
    }

    /**
     * 使用 ErrorCode 枚举 + 原因异常构造
     *
     * @param errorCode 错误码枚举
     * @param cause     原因异常
     */
    public BusinessException(ErrorCode errorCode, Throwable cause) {
        super(errorCode.getMessage(), cause);
        this.errorCode = errorCode.getCode();
    }

    /**
     * 使用 ErrorCode 枚举 + 自定义消息 + 原因异常构造
     *
     * @param errorCode 错误码枚举
     * @param message   自定义错误消息
     * @param cause     原因异常
     */
    public BusinessException(ErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode.getCode();
    }

    /**
     * 仅使用消息构造（向后兼容，默认使用系统错误码）
     *
     * @param message 错误消息
     * @deprecated 建议使用带 ErrorCode 的构造方法
     */
    @Deprecated
    public BusinessException(String message) {
        super(message);
        this.errorCode = ErrorCode.SYSTEM_ERROR.getCode();
    }

    /**
     * 使用消息和原因异常构造（向后兼容）
     *
     * @param message 错误消息
     * @param cause   原因异常
     * @deprecated 建议使用带 ErrorCode 的构造方法
     */
    @Deprecated
    public BusinessException(String message, Throwable cause) {
        super(message, cause);
        this.errorCode = ErrorCode.SYSTEM_ERROR.getCode();
    }
}