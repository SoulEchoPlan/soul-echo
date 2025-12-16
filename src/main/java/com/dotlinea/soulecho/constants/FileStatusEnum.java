package com.dotlinea.soulecho.constants;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 知识库文件状态枚举
 * <p>
 * 定义知识库文件在上传和索引过程中的各种状态
 * </p>
 *
 * @author fanfan187
 * @version v1.0.0
 * @since v1.0.0
 */
@Getter
@AllArgsConstructor
public enum FileStatusEnum {

    /**
     * 上传中 - 文件正在上传到阿里云百炼
     */
    UPLOADING("UPLOADING", "上传中"),

    /**
     * 索引中 - 文件已上传成功，正在构建索引
     */
    INDEXING("INDEXING", "索引中"),

    /**
     * 已完成 - 文件上传和索引均已成功完成
     */
    COMPLETED("COMPLETED", "已完成"),

    /**
     * 失败 - 文件上传或索引失败
     */
    FAILED("FAILED", "失败"),

    /**
     * 活跃 - 文档处于可用状态
     */
    ACTIVE("ACTIVE", "活跃");

    /**
     * 状态代码
     */
    private final String code;

    /**
     * 状态描述
     */
    private final String description;

    /**
     * 根据代码获取枚举
     *
     * @param code 状态代码
     * @return 对应的枚举值
     * @throws IllegalArgumentException 如果代码不存在
     */
    public static FileStatusEnum fromCode(String code) {
        for (FileStatusEnum status : values()) {
            if (status.code.equals(code)) {
                return status;
            }
        }
        throw new IllegalArgumentException("未知的文件状态代码: " + code);
    }

    /**
     * 检查代码是否有效
     *
     * @param code 状态代码
     * @return 是否有效
     */
    public static boolean isValidCode(String code) {
        try {
            fromCode(code);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}