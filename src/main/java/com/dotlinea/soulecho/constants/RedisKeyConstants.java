package com.dotlinea.soulecho.constants;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * Redis 键前缀常量
 * <p>
 * 统一管理所有 Redis 键的命名规范和前缀
 * </p>
 *
 * @author fanfan187
 * @version v1.0.0
 * @since v1.0.0
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class RedisKeyConstants {

    /**
     * 会话历史记录 Redis 键前缀
     * <p>
     * 格式: soul-echo:session:history:{sessionId}
     * </p>
     */
    public static final String SESSION_HISTORY_PREFIX = "soul-echo:session:history:";

    /**
     * 会话分布式锁 Redis 键前缀
     * <p>
     * 格式: soul-echo:session:lock:{sessionId}
     * </p>
     */
    public static final String SESSION_LOCK_PREFIX = "soul-echo:session:lock:";
}