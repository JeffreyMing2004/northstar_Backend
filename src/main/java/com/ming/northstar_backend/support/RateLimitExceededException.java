package com.ming.northstar_backend.support;

/**
 * 触发访问频率限制时抛出，由各 Controller 映射为 HTTP 429。
 *
 * <p>单独成一个类型是为了把「限流」与「参数错误」区分开：前者必须回 429（前端据此
 * 提示「操作过于频繁」而不是「格式错误」），后者仍是 400。</p>
 */
public class RateLimitExceededException extends RuntimeException {

    private final long retryAfterSeconds;

    public RateLimitExceededException(String message) {
        this(message, 60);
    }

    public RateLimitExceededException(String message, long retryAfterSeconds) {
        super(message);
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
