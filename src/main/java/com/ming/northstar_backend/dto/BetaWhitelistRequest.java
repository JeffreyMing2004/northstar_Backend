package com.ming.northstar_backend.dto;

/**
 * 新增 / 修改白名单条目的请求体。
 *
 * <p>字段全部可选（新增时 {@code qq} 必填），修改时只传需要改的字段即可，
 * 未传的字段保持原值不变。</p>
 *
 * <p>{@code mcId} 是绑定的游戏 ID：留空表示只绑 QQ、不限定游戏 ID（该 QQ 用任意
 * 游戏 ID 都能通过）；填写后必须与客户端上报的名字一致（忽略大小写）。</p>
 */
public class BetaWhitelistRequest {

    private String qq;
    /** 绑定的游戏 ID（Minecraft ID），留空表示不限定。 */
    private String mcId;
    private String nickname;
    private String remark;
    /** 1 启用 / 0 禁用。 */
    private Integer status;
    /**
     * 到期时间，支持 {@code yyyy-MM-dd}、{@code yyyy-MM-dd HH:mm}、
     * {@code yyyy-MM-ddTHH:mm:ss} 三种写法；传空字符串表示「永不过期」。
     */
    private String expireAt;

    public BetaWhitelistRequest() {
    }

    public String getQq() { return qq; }
    public void setQq(String qq) { this.qq = qq; }
    public String getMcId() { return mcId; }
    public void setMcId(String mcId) { this.mcId = mcId; }
    public String getNickname() { return nickname; }
    public void setNickname(String nickname) { this.nickname = nickname; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }
    public String getExpireAt() { return expireAt; }
    public void setExpireAt(String expireAt) { this.expireAt = expireAt; }
}
