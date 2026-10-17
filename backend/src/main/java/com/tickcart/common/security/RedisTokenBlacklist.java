package com.tickcart.common.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

/**
 * 基于 Redis 的 access token 吊销名单。
 *
 * <p>两类 key：</p>
 * <ul>
 *   <li>{@code auth:revoked:jti:{jti}} —— 单令牌，TTL = 剩余有效期</li>
 *   <li>{@code auth:invalid-before:{userId}} —— 用户维度的失效时间点</li>
 * </ul>
 *
 * <p>「失效时间点」只增不减：并发改密时取较晚的那个，避免后到的请求把已失效的令牌又「复活」。</p>
 */
@Component
public class RedisTokenBlacklist implements TokenBlacklist {

    private static final Logger log = LoggerFactory.getLogger(RedisTokenBlacklist.class);

    private static final String NS = "auth:";
    private static final String JTI_PREFIX = NS + "revoked:jti:";
    private static final String USER_PREFIX = NS + "invalid-before:";

    private final StringRedisTemplate redis;

    public RedisTokenBlacklist(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public void revoke(String jti, long ttlSeconds) {
        if (jti == null || ttlSeconds <= 0) {
            return;
        }
        try {
            redis.opsForValue().set(JTI_PREFIX + jti, "1", Duration.ofSeconds(ttlSeconds));
        } catch (DataAccessException ex) {
            failOpen("拉黑令牌", ex);
        }
    }

    @Override
    public boolean isRevoked(String jti) {
        if (jti == null) {
            // 没有 jti 的是本功能上线前签发的旧令牌，无从判断，按未吊销处理
            return false;
        }
        try {
            return Boolean.TRUE.equals(redis.hasKey(JTI_PREFIX + jti));
        } catch (DataAccessException ex) {
            failOpen("查询令牌吊销", ex);
            return false;
        }
    }

    @Override
    public void revokeAllIssuedBefore(Long userId, long ttlSeconds) {
        if (userId == null || ttlSeconds <= 0) {
            return;
        }
        try {
            long now = Instant.now().getEpochSecond();
            String key = USER_PREFIX + userId;
            String current = redis.opsForValue().get(key);
            long existing = current == null ? 0L : Long.parseLong(current);
            if (existing < now) {
                redis.opsForValue().set(key, String.valueOf(now), Duration.ofSeconds(ttlSeconds));
            }
        } catch (DataAccessException ex) {
            failOpen("按用户吊销", ex);
        }
    }

    @Override
    public boolean isIssuedBefore(Long userId, long issuedAtEpochSecond) {
        if (userId == null) {
            return false;
        }
        try {
            String value = redis.opsForValue().get(USER_PREFIX + userId);
            if (value == null) {
                return false;
            }
            // 严格小于：改密后同一秒内重新登录拿到的新令牌不该被误杀
            return issuedAtEpochSecond < Long.parseLong(value);
        } catch (DataAccessException ex) {
            failOpen("查询用户吊销", ex);
            return false;
        }
    }

    private void failOpen(String action, DataAccessException ex) {
        log.warn("令牌吊销检查降级放行（{}）：Redis 不可用 - {}", action, ex.getMessage());
    }
}
