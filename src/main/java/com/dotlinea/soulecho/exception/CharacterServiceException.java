package com.dotlinea.soulecho.exception;

/**
 * 角色服务异常
 * <p>
 * 用于封装角色服务层的业务异常，提供更精确的错误信息
 * </p>
 *
 * @author fanfan187
 * @version v1.0.0
 * @since v1.0.0
 */
public class CharacterServiceException extends RuntimeException {

    public CharacterServiceException(String message) {
        super(message);
    }

    public CharacterServiceException(String message, Throwable cause) {
        super(message, cause);
    }
}