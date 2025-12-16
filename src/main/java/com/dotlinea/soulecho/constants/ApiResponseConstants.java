package com.dotlinea.soulecho.constants;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * API 响应常量
 * <p>
 * 统一管理 HTTP API 响应中使用的常量字符串
 * </p>
 *
 * @author fanfan187
 * @version v1.0.0
 * @since v1.0.0
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ApiResponseConstants {

    /**
     * API 响应状态 - 成功
     */
    public static final String STATUS_SUCCESS = "SUCCESS";

    /**
     * API 响应状态 - 错误
     */
    public static final String STATUS_ERROR = "ERROR";

    /**
     * 响应体字段名 - 状态
     */
    public static final String FIELD_STATUS = "status";

    /**
     * 响应体字段名 - 错误信息
     */
    public static final String FIELD_ERROR = "error";

    /**
     * 响应体字段名 - 消息
     */
    public static final String FIELD_MESSAGE = "message";

    /**
     * 响应体字段名 - 数据
     */
    public static final String FIELD_DATA = "data";
}