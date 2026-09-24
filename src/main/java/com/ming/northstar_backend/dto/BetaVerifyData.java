package com.ming.northstar_backend.dto;

import com.ming.northstar_backend.entity.BetaWhitelist;

import java.time.format.DateTimeFormatter;

/**
 * 校验通过时返回给客户端的扩展信息（客户端目前仅打印/忽略，不影响判定）。
 */
public class BetaVerifyData {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private String qq;
    private String nickname;
    /** 白名单里绑定的游戏 ID；未绑定时为 null。 */
    private String gameId;
    /**
     * 命中方式：{@code bound} = 与绑定的游戏ID 精确一致；
     * {@code unbound} = 该 QQ 未绑定游戏ID，按任意游戏ID 放行。
     */
    private String matchedBy;
    /** 内测类型，取自后台记录；未填写时回落到「标准内测资格」。 */
    private String type;
    /** 到期日，yyyy-MM-dd；长期有效时为 null。 */
    private String expire;
    /** 客户端上报的游戏 ID 原样回显，便于日志比对。 */
    private String playerName;

    public BetaVerifyData() {
    }

    public static BetaVerifyData from(BetaWhitelist entry, String playerName, String matchedBy) {
        BetaVerifyData data = new BetaVerifyData();
        data.qq = entry.getQq();
        data.nickname = blankToNull(entry.getNickname());
        data.gameId = entry.getBoundGameId();
        data.matchedBy = matchedBy;
        data.type = blankToNull(entry.getRemark()) == null ? "标准内测资格" : entry.getRemark();
        data.expire = entry.getExpireAt() == null ? null : entry.getExpireAt().format(DATE);
        data.playerName = playerName;
        return data;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    public String getQq() { return qq; }
    public void setQq(String qq) { this.qq = qq; }
    public String getNickname() { return nickname; }
    public void setNickname(String nickname) { this.nickname = nickname; }
    public String getGameId() { return gameId; }
    public void setGameId(String gameId) { this.gameId = gameId; }
    public String getMatchedBy() { return matchedBy; }
    public void setMatchedBy(String matchedBy) { this.matchedBy = matchedBy; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getExpire() { return expire; }
    public void setExpire(String expire) { this.expire = expire; }
    public String getPlayerName() { return playerName; }
    public void setPlayerName(String playerName) { this.playerName = playerName; }
}
