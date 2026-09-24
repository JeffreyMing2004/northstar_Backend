package com.ming.northstar_backend.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * 内测资格校验日志。
 *
 * <p>客户端 Mod 每次调用 {@code GET /api/beta/verify} 都会写入一条，
 * 用于排查「为什么某个玩家被拒绝/崩溃」以及统计。</p>
 *
 * <p>不记录 UUID：离线模式下 UUID 不稳定，记录它没有排查价值。</p>
 */
@Entity
@Table(name = "northstar_verify_log")
public class BetaVerifyLog {

    /** 结果：通过。 */
    public static final String RESULT_PASS = "pass";
    /** 结果：明确未通过（客户端会崩溃退出）。 */
    public static final String RESULT_REJECT = "reject";
    /** 结果：服务异常 / 参数非法 / 被限流（客户端可重试）。 */
    public static final String RESULT_ERROR = "error";

    /** 命中方式：白名单绑定的游戏ID 与上报名精确一致。 */
    public static final String MATCH_BOUND = "bound";
    /** 命中方式：白名单未绑定游戏ID，任意名字放行。 */
    public static final String MATCH_UNBOUND = "unbound";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 16)
    private String qq;

    /** 客户端上报的游戏 ID（Minecraft 玩家名）。 */
    @Column(length = 64)
    private String playerName;

    @Column(length = 64)
    private String ip;

    @Column(length = 255)
    private String userAgent;

    /** {@link #RESULT_PASS} / {@link #RESULT_REJECT} / {@link #RESULT_ERROR}。 */
    @Column(length = 16)
    private String result;

    /** 通过时的命中方式：{@link #MATCH_BOUND} / {@link #MATCH_UNBOUND}。 */
    @Column(length = 16)
    private String matchedBy;

    /** 返回给客户端的 HTTP 状态码。 */
    private Integer httpStatus;

    /** 处理耗时（毫秒）。 */
    private Long costMs;

    /** 未通过原因简述。 */
    @Column(length = 128)
    private String reason;

    private LocalDateTime createdAt = LocalDateTime.now();

    public BetaVerifyLog() {
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getQq() { return qq; }
    public void setQq(String qq) { this.qq = qq; }
    public String getPlayerName() { return playerName; }
    public void setPlayerName(String playerName) { this.playerName = playerName; }
    public String getIp() { return ip; }
    public void setIp(String ip) { this.ip = ip; }
    public String getUserAgent() { return userAgent; }
    public void setUserAgent(String userAgent) { this.userAgent = userAgent; }
    public String getResult() { return result; }
    public void setResult(String result) { this.result = result; }
    public String getMatchedBy() { return matchedBy; }
    public void setMatchedBy(String matchedBy) { this.matchedBy = matchedBy; }
    public Integer getHttpStatus() { return httpStatus; }
    public void setHttpStatus(Integer httpStatus) { this.httpStatus = httpStatus; }
    public Long getCostMs() { return costMs; }
    public void setCostMs(Long costMs) { this.costMs = costMs; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
