package com.ming.northstar_backend.dto;

import com.ming.northstar_backend.entity.BetaVerifyLog;

import java.time.format.DateTimeFormatter;

public class BetaVerifyLogDto {

    private static final DateTimeFormatter DATE_TIME =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private Long id;
    /** 脱敏后的 QQ 号，如 123****89；默认不返回完整 QQ。 */
    private String qq;
    /** 客户端上报的游戏 ID。 */
    private String playerName;
    private String ip;
    private String userAgent;
    private String result;
    /** 通过时的命中方式：bound / unbound。 */
    private String matchedBy;
    private Integer httpStatus;
    private Long costMs;
    private String reason;
    private String createdAt;

    public BetaVerifyLogDto() {
    }

    /** 默认脱敏输出。 */
    public static BetaVerifyLogDto from(BetaVerifyLog log) {
        return from(log, true);
    }

    /**
     * @param mask true 时对 QQ 号脱敏；后台需要精确排查时传 false
     */
    public static BetaVerifyLogDto from(BetaVerifyLog log, boolean mask) {
        BetaVerifyLogDto dto = new BetaVerifyLogDto();
        dto.id = log.getId();
        dto.qq = mask ? maskQq(log.getQq()) : log.getQq();
        dto.playerName = log.getPlayerName();
        dto.ip = log.getIp();
        dto.userAgent = log.getUserAgent();
        dto.result = log.getResult();
        dto.matchedBy = log.getMatchedBy();
        dto.httpStatus = log.getHttpStatus();
        dto.costMs = log.getCostMs();
        dto.reason = log.getReason();
        dto.createdAt = log.getCreatedAt() == null ? null : log.getCreatedAt().format(DATE_TIME);
        return dto;
    }

    /** 日志脱敏：QQ 号只保留前 3 位与后 2 位。 */
    public static String maskQq(String qq) {
        if (qq == null || qq.isBlank()) {
            return qq;
        }
        if (qq.length() <= 5) {
            return qq.charAt(0) + "***";
        }
        return qq.substring(0, 3) + "****" + qq.substring(qq.length() - 2);
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
    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
}
