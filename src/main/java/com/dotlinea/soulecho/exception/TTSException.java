package com.dotlinea.soulecho.exception;

import lombok.Getter;

/**
 * TTS 服务相关异常
 * <p>
 * 用于区分不同类型的语音合成服务错误，特别是Token过期、服务未开通等场景
 * </p>
 *
 * @author fanfan187
 * @version v1.0.0
 * @since v1.0.0
 */
@Getter
public class TTSException extends RuntimeException {

    /**
     * 错误码
     */
    private final int statusCode;

    /**
     * 错误文本
     */
    private final String statusText;

    /**
     * 是否为Token过期
     */
    private final boolean isTokenExpired;

    /**
     * 用户友好的错误提示
     */
    private final String userFriendlyMessage;

    public TTSException(String message, int statusCode, String statusText) {
        super(message);
        this.statusCode = statusCode;
        this.statusText = statusText;
        this.isTokenExpired = statusCode == 418 ||
                             statusText.contains("TOKEN_INVALID") ||
                             statusText.contains("41020001");
        this.userFriendlyMessage = buildUserFriendlyMessage(statusCode, statusText);
    }

    public TTSException(String message, int statusCode, String statusText, Throwable cause) {
        super(message, cause);
        this.statusCode = statusCode;
        this.statusText = statusText;
        this.isTokenExpired = statusCode == 418 ||
                             statusText.contains("TOKEN_INVALID") ||
                             statusText.contains("41020001");
        this.userFriendlyMessage = buildUserFriendlyMessage(statusCode, statusText);
    }

    /**
     * 根据错误码生成用户友好的错误提示
     *
     * @param statusCode 错误码
     * @param statusText 错误文本
     * @return 用户友好的错误提示
     */
    private String buildUserFriendlyMessage(int statusCode, String statusText) {
        // Token过期或无效
        if (statusCode == 418 || statusText.contains("TOKEN_INVALID") ||
            statusText.contains("41020001")) {
            return "语音服务认证已更新，本次回复已切换至文字模式，下次即可恢复语音";
        }

        // 免费试用过期
        if (statusCode == 40000010 || statusText.contains("FREE_TRIAL_EXPIRED")) {
            return "语音服务试用已到期，请联系管理员续费。错误码: " + statusCode;
        }

        // 认证失败
        if (statusCode == 40000003 || statusText.contains("AUTH_FAIL")) {
            return "语音服务认证失败，请检查API配置。错误码: " + statusCode;
        }

        // 请求过于频繁
        if (statusCode == 40000005 || statusText.contains("TOO_MANY_REQUESTS")) {
            return "语音请求过于频繁，请稍后再试。错误码: " + statusCode;
        }

        // 服务端错误
        if (statusCode >= 500) {
            return "语音服务暂时不可用，已切换至文字模式。错误码: " + statusCode;
        }

        // 默认错误提示
        return "语音合成异常: " + statusText + " (错误码: " + statusCode + ")";
    }
}
