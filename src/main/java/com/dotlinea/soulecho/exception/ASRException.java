package com.dotlinea.soulecho.exception;

import lombok.Getter;

/**
 * ASR 服务相关异常
 * <p>
 * 用于区分不同类型的语音识别服务错误，特别是免费试用过期等需要用户处理的场景
 * </p>
 *
 * @author fanfan187
 * @version v1.0.0
 * @since v1.0.0
 */
@Getter
public class ASRException extends RuntimeException {

    /**
     * 错误码
     */
    private final int statusCode;

    /**
     * 是否为免费试用过期
     */
    private final boolean isTrialExpired;

    /**
     * 用户友好的错误提示
     */
    private final String userFriendlyMessage;

    public ASRException(String message, int statusCode, String statusText) {
        super(message);
        this.statusCode = statusCode;
        this.isTrialExpired = statusCode == 40000010 || statusText.contains("FREE_TRIAL_EXPIRED");
        this.userFriendlyMessage = buildUserFriendlyMessage(statusCode, statusText);
    }

    public ASRException(String message, int statusCode, String statusText, Throwable cause) {
        super(message, cause);
        this.statusCode = statusCode;
        this.isTrialExpired = statusCode == 40000010 || statusText.contains("FREE_TRIAL_EXPIRED");
        this.userFriendlyMessage = buildUserFriendlyMessage(statusCode, statusText);
    }


    /**
     * 根据错误码生成用户友好的错误提示
     *
     * @param statusCode  错误码
     * @param statusText  错误文本
     * @return 用户友好的错误提示
     */
    private String buildUserFriendlyMessage(int statusCode, String statusText) {
        // 免费试用过期
        if (statusCode == 40000010 || statusText.contains("FREE_TRIAL_EXPIRED")) {
            return "语音识别服务试用已到期，请联系管理员开通服务。错误码: " + statusCode;
        }

        // 认证失败
        if (statusCode == 40000003 || statusText.contains("AUTH_FAIL")) {
            return "语音识别服务认证失败，请检查配置。错误码: " + statusCode;
        }

        // 请求过于频繁
        if (statusCode == 40000005 || statusText.contains("TOO_MANY_REQUESTS")) {
            return "语音识别请求过于频繁，请稍后再试。错误码: " + statusCode;
        }

        // 服务端错误
        if (statusCode >= 500) {
            return "语音识别服务暂时不可用，请稍后重试。错误码: " + statusCode;
        }

        // 默认错误提示
        return "语音识别服务异常: " + statusText + " (错误码: " + statusCode + ")";
    }
}
