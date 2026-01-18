package com.dotlinea.soulecho.client;

import com.alibaba.nls.client.AccessToken;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.util.Date;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * TTS Token管理器 - 支持主动刷新和被动刷新
 * <p>
 * 负责阿里云NLS Access Token的自动刷新，采用双重刷新策略：
 * <ul>
 * <li>主动刷新：每20小时定时刷新Token，避免Token过期</li>
 * <li>被动刷新：在getValidToken()方法中检查Token是否即将过期，如果即将过期则刷新</li>
 * </ul>
 * </p>
 * <p>
 * 并发安全：使用ReadWriteLock保证高并发场景下的线程安全
 * </p>
 *
 * @author fanfan187
 * @version v1.0.0
 * @since v1.0.0
 */
@Slf4j
@Component
public class TTSTokenManager {

    @Value("${tts.api.key}")
    private String apiKey;

    @Value("${tts.api.secret}")
    private String apiSecret;

    /**
     * Token提前刷新时间窗口（毫秒）
     * 在Token过期前5分钟就刷新，避免临界时刻的并发问题
     */
    private static final long REFRESH_ADVANCE_MS = 5 * 60 * 1000;

    /**
     * Token主动刷新间隔（毫秒）
     * 每20小时主动刷新一次Token
     */
    private static final long SCHEDULED_REFRESH_INTERVAL_MS = 20 * 60 * 60 * 1000;

    private volatile String currentToken;
    private volatile long expireTimeMillis;

    private final ReadWriteLock lock = new ReentrantReadWriteLock();

    /**
     * 启动时初始化Token
     */
    @PostConstruct
    public void init() {
        // 验证配置
        if (apiKey == null || apiKey.trim().isEmpty()) {
            throw new IllegalStateException("TTS API Key未配置，请检查application.properties");
        }
        if (apiSecret == null || apiSecret.trim().isEmpty()) {
            throw new IllegalStateException("TTS API Secret未配置，请检查application.properties");
        }

        log.info("初始化TTS Token管理器");

        // 尝试获取Token，验证配置是否正确
        try {
            refreshToken();
            log.info("TTS配置验证成功，Token将在 {} 后过期",
                new Date(expireTimeMillis));
        } catch (Exception e) {
            throw new IllegalStateException("TTS配置验证失败，请检查API Key/Secret是否正确", e);
        }
    }

    /**
     * 获取有效的Token，如果即将过期则自动刷新
     * <p>
     * 使用双重检查锁（Double-Checked Locking）优化性能：
     * <ol>
     * <li>快速路径：使用读锁检查是否需要刷新</li>
     * <li>慢路径：如果需要刷新，使用写锁刷新Token（双重检查）</li>
     * </ol>
     * </p>
     *
     * @return 有效的Access Token
     */
    public String getValidToken() {
        // 快速路径：使用读锁检查
        lock.readLock().lock();
        try {
            if (!shouldRefresh()) {
                return currentToken;
            }
        } finally {
            lock.readLock().unlock();
        }

        // 慢路径：需要刷新Token，使用写锁
        lock.writeLock().lock();
        try {
            // 双重检查锁
            if (shouldRefresh()) {
                refreshToken();
            }
            return currentToken;
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * 判断是否需要刷新Token
     */
    private boolean shouldRefresh() {
        return currentToken == null ||
               System.currentTimeMillis() > (expireTimeMillis - REFRESH_ADVANCE_MS);
    }

    /**
     * 刷新Token
     */
    private void refreshToken() {
        try {
            log.info("开始刷新TTS Token...");
            AccessToken accessToken = new AccessToken(apiKey, apiSecret);
            accessToken.apply();

            currentToken = accessToken.getToken();
            expireTimeMillis = accessToken.getExpireTime();

            log.info("TTS Token刷新成功，过期时间: {}",
                accessToken.getExpireTime());
        } catch (Exception e) {
            log.error("刷新TTS Token失败", e);
            throw new RuntimeException("TTS Token刷新失败", e);
        }
    }

    /**
     * 强制刷新Token（用于测试或手动触发）
     */
    public void forceRefresh() {
        lock.writeLock().lock();
        try {
            refreshToken();
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * 定时刷新Token - 主动刷新策略
     * <p>
     * 每20小时自动刷新Token，避免Token过期导致服务不可用。
     * 使用fixedDelay确保上一次刷新完成后才计算下一次刷新时间。
     * </p>
     * <p>
     * 定时任务配置：
     * - initialDelay：启动后1小时首次执行（让服务先稳定运行）
     * - fixedDelay：每次刷新完成后间隔20小时再执行下一次刷新
     * </p>
     */
    @Scheduled(initialDelay = 60 * 60 * 1000, fixedDelay = SCHEDULED_REFRESH_INTERVAL_MS)
    public void scheduledRefreshToken() {
        log.info("【定时任务】开始主动刷新TTS Token...");

        lock.writeLock().lock();
        try {
            refreshToken();
            log.info("【定时任务】TTS Token刷新成功，下次过期时间: {}", new Date(expireTimeMillis));
        } catch (Exception e) {
            log.error("【定时任务】TTS Token刷新失败", e);
            // 注意：定时任务刷新失败不影响服务运行，下次调用getValidToken()时会触发被动刷新
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * 获取Token过期时间（用于监控）
     *
     * @return Token过期时间（Date对象）
     */
    public Date getExpireTime() {
        return new Date(expireTimeMillis);
    }

    /**
     * 检查Token是否有效（用于健康检查）
     *
     * @return true表示Token有效，false表示Token无效或即将过期
     */
    public boolean isTokenValid() {
        lock.readLock().lock();
        try {
            return currentToken != null &&
                   System.currentTimeMillis() < (expireTimeMillis - REFRESH_ADVANCE_MS);
        } finally {
            lock.readLock().unlock();
        }
    }
}
