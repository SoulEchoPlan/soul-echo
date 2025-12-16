package com.dotlinea.soulecho.dto;

import com.dotlinea.soulecho.exception.ErrorCode;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 统一 API 响应封装
 * <p>
 * 所有 HTTP API 接口统一使用此类型返回，保证响应格式一致性
 * </p>
 *
 * @param <T> 响应数据类型
 * @author fanfan187
 * @version v1.0.0
 * @since v1.0.0
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {

    /**
     * 错误码，0 表示成功
     */
    private int code;

    /**
     * 响应消息
     */
    private String message;

    /**
     * 响应数据
     */
    private T data;

    /**
     * 时间戳
     */
    private long timestamp;

    // ==================== 静态工厂方法 ====================

    /**
     * 成功响应（无数据）
     *
     * @param <T> 数据类型
     * @return ApiResponse 实例
     */
    public static <T> ApiResponse<T> success() {
        return new ApiResponse<>(
                ErrorCode.SUCCESS.getCode(),
                ErrorCode.SUCCESS.getMessage(),
                null,
                System.currentTimeMillis()
        );
    }

    /**
     * 成功响应（带数据）
     *
     * @param data 响应数据
     * @param <T>  数据类型
     * @return ApiResponse 实例
     */
    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(
                ErrorCode.SUCCESS.getCode(),
                ErrorCode.SUCCESS.getMessage(),
                data,
                System.currentTimeMillis()
        );
    }

    /**
     * 成功响应（带消息和数据）
     *
     * @param message 响应消息
     * @param data    响应数据
     * @param <T>     数据类型
     * @return ApiResponse 实例
     */
    public static <T> ApiResponse<T> success(String message, T data) {
        return new ApiResponse<>(
                ErrorCode.SUCCESS.getCode(),
                message,
                data,
                System.currentTimeMillis()
        );
    }

    /**
     * 错误响应（使用 ErrorCode）
     *
     * @param errorCode 错误码枚举
     * @param <T>       数据类型
     * @return ApiResponse 实例
     */
    public static <T> ApiResponse<T> error(ErrorCode errorCode) {
        return new ApiResponse<>(
                errorCode.getCode(),
                errorCode.getMessage(),
                null,
                System.currentTimeMillis()
        );
    }

    /**
     * 错误响应（使用 ErrorCode + 自定义消息）
     *
     * @param errorCode 错误码枚举
     * @param message   自定义错误消息
     * @param <T>       数据类型
     * @return ApiResponse 实例
     */
    public static <T> ApiResponse<T> error(ErrorCode errorCode, String message) {
        return new ApiResponse<>(
                errorCode.getCode(),
                message,
                null,
                System.currentTimeMillis()
        );
    }

    /**
     * 错误响应（自定义错误码和消息）
     *
     * @param code    错误码
     * @param message 错误消息
     * @param <T>     数据类型
     * @return ApiResponse 实例
     */
    public static <T> ApiResponse<T> error(int code, String message) {
        return new ApiResponse<>(
                code,
                message,
                null,
                System.currentTimeMillis()
        );
    }
}