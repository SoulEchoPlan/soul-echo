package com.dotlinea.soulecho.exception;

/**
 * 业务异常
 * <p>
 * 当业务逻辑出现错误时抛出此异常
 * </p>
 *
 * @author fanfan187
 * @version v1.0.0
 * @since v1.0.0
 */
public class BusinessException extends RuntimeException {

    public BusinessException(String message) {
        super(message);
    }

    public BusinessException(String message, Throwable cause) {
        super(message, cause);
    }
}