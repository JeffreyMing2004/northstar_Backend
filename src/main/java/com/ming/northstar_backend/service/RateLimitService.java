package com.ming.northstar_backend.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * 基于 Redis 的固定窗口计数器，供全局限流 / 失败计数使用。
 *
 * <p>为什么不用进程内存（原 {@code BetaWhitelistService} 的滑动窗口）：渗透报告 NS-09
 * 指出内存窗口有两个硬伤——① 反代未还原真实 IP 时所有人共享同一个桶（正常玩家被误判 429），
 * ② 攻击者跨节点/跨实例分散请求即可绕过，重启进程也会把计数清零。放 Redis 后，
 * 计数与来源 IP 无关地全局一致，多实例部署也能生效。</p>
 *
 * <p><b>失败策略是「放行」而不是「拒绝」</b>：Redis 不可用时若一律拒绝，等于把限流组件
 * 变成了单点故障（整个站点登录不可用）。这里选择记一条 WARN 后放行，可用性优先。</p>
 */
@Service
public class RateLimitService {

    private static final Logger log = LoggerFactory.getLogger(RateLimitService.class);
    private static final String PREFIX = "northstar:rl:";

    private final StringRedisTemplate redis;

    public RateLimitService(StringRedisTemplate redis) {
        this.redis = redis;
    }

    /**
     * 记一次命中并判断是否超限。
     *
     * @param bucket 业务分组（如 {@code login-ip}），与 key 组成完整的限流键
     * @param key    分组内的维度，例如来源 IP 或账号
     * @param limit  窗口内允许的最大次数，{@code <= 0} 表示不限流（直接放行）
     * @param window 窗口长度
     * @return {@code true} 表示允许，{@code false} 表示已超限
     */
    public boolean allow(String bucket, String key, int limit, Duration window) {
        if (limit <= 0) {
            return true;
        }
        String redisKey = key(bucket, key);
        try {
            Long hits = redis.opsForValue().increment(redisKey);
            if (hits == null) {
                return true;
            }
            if (hits == 1L) {
                redis.expire(redisKey, window);
            }
            return hits <= limit;
        } catch (Exception e) {
            log.warn("[NorthStar] 限流计数失败（{}），本次放行：{}", redisKey, e.getMessage());
            return true;
        }
    }

    /** 读取窗口内的命中次数；Redis 不可用时返回 0。 */
    public long hits(String bucket, String key) {
        try {
            String value = redis.opsForValue().get(key(bucket, key));
            return value == null ? 0L : Long.parseLong(value);
        } catch (Exception e) {
            return 0L;
        }
    }

    /** 写入计数（用于「先失败后计数」的场景，例如登录失败次数）。 */
    public long increment(String bucket, String key, Duration window) {
        String redisKey = key(bucket, key);
        try {
            Long hits = redis.opsForValue().increment(redisKey);
            if (hits != null && hits == 1L) {
                redis.expire(redisKey, window);
            }
            return hits == null ? 0L : hits;
        } catch (Exception e) {
            log.warn("[NorthStar] 失败计数写入失败（{}）：{}", redisKey, e.getMessage());
            return 0L;
        }
    }

    /** 清除计数（登录成功、验证码校验成功等）。 */
    public void clear(String bucket, String key) {
        try {
            redis.delete(key(bucket, key));
        } catch (Exception e) {
            log.warn("[NorthStar] 限流计数清除失败：{}", e.getMessage());
        }
    }

    /** 剩余窗口秒数；取不到时返回 0。 */
    public long ttlSeconds(String bucket, String key) {
        try {
            Long ttl = redis.getExpire(key(bucket, key));
            return ttl == null || ttl < 0 ? 0L : ttl;
        } catch (Exception e) {
            return 0L;
        }
    }

    private String key(String bucket, String key) {
        String dimension = key == null || key.isBlank() ? "unknown" : key.trim().toLowerCase(java.util.Locale.ROOT);
        return PREFIX + bucket + ":" + dimension;
    }
}
