package com.ming.northstar_backend.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Date;

/**
 * 令牌吊销（按「用户 × 签发时间」判定）。
 *
 * <p>JWT 是无状态的，签发出去就收不回来——改密码、重置密码、管理员撤销账号后，
 * 旧令牌只要没过期就仍然能用（渗透报告 NS-01 的提权链路正是建立在这一点上）。
 * 这里用 Redis 记一个「该用户在某时刻之前签发的令牌全部作废」的时间戳，
 * 由 {@code JwtFilter} 在每次请求时比对令牌的 {@code iat}，即可做到即时吊销。</p>
 *
 * <p>相比在 {@code users} 表加 {@code token_version} 列再改判权逻辑，时间戳方案不需要
 * 改表结构（生产 ddl-auto 已收敛为 validate），也不会被 Hibernate 自动改表误伤。</p>
 *
 * <p>Redis 不可用时<b>放行</b>并记 WARN：吊销是「加固」，不能让它变成登录不可用的
 * 单点故障。</p>
 */
@Service
public class TokenRevocationService {

    private static final Logger log = LoggerFactory.getLogger(TokenRevocationService.class);
    private static final String PREFIX = "northstar:token-revoked-before:";

    private final StringRedisTemplate redis;
    private final long retentionSeconds;

    public TokenRevocationService(StringRedisTemplate redis,
                                  @Value("${northstar.auth.revocation-retention-seconds:2592000}") long retentionSeconds) {
        this.redis = redis;
        this.retentionSeconds = retentionSeconds;
    }

    /** 作废该用户当前所有已签发令牌（重置密码 / 改密后调用）。 */
    public void revokeAllForUser(Long userId) {
        if (userId == null) {
            return;
        }
        try {
            redis.opsForValue().set(key(userId), String.valueOf(nowEpochSeconds()),
                    Duration.ofSeconds(Math.max(retentionSeconds, 3600L)));
        } catch (Exception e) {
            log.warn("[NorthStar] 令牌吊销标记写入失败（user={}）：{}", userId, e.getMessage());
        }
    }

    /** 令牌是否已被吊销：签发时间早于（含）吊销时刻即视为失效。 */
    public boolean isRevoked(Long userId, Date issuedAt) {
        if (userId == null || issuedAt == null) {
            return false;
        }
        try {
            String value = redis.opsForValue().get(key(userId));
            if (value == null) {
                return false;
            }
            long revokedBefore = Long.parseLong(value);
            // iat 精度为秒，同一秒内重新签发的令牌会被误伤，因此生成新令牌时会额外 +1s（见 JwtUtil）
            return issuedAt.toInstant().getEpochSecond() <= revokedBefore;
        } catch (Exception e) {
            log.warn("[NorthStar] 令牌吊销校验失败（user={}），本次放行：{}", userId, e.getMessage());
            return false;
        }
    }

    private String key(Long userId) {
        return PREFIX + userId;
    }

    private long nowEpochSeconds() {
        return Instant.now().getEpochSecond();
    }
}
