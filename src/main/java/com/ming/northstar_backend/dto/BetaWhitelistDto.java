package com.ming.northstar_backend.dto;

import com.ming.northstar_backend.entity.BetaWhitelist;

import java.time.format.DateTimeFormatter;

public class BetaWhitelistDto {

    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private Long id;
    private String qq;
    /** 绑定的游戏 ID；为空表示不限定游戏 ID。 */
    private String mcId;
    /** 是否已绑定游戏 ID（false 时该 QQ 用任意游戏 ID 都能通过）。 */
    private Boolean gameIdBound;
    private String nickname;
    private String remark;
    private Integer status;
    /** 到期时间，yyyy-MM-dd HH:mm；永不过期为 null。 */
    private String expireAt;
    /** 到期日 yyyy-MM-dd，便于前端直接展示。 */
    private String expireDate;
    /** 是否当前可用（启用且未过期）。 */
    private Boolean active;
    private String createdAt;
    private String updatedAt;
    private String createdBy;

    public BetaWhitelistDto() {
    }

    public static BetaWhitelistDto from(BetaWhitelist entry, boolean active) {
        BetaWhitelistDto dto = new BetaWhitelistDto();
        dto.id = entry.getId();
        dto.qq = entry.getQq();
        dto.mcId = entry.getBoundGameId();
        dto.gameIdBound = entry.getBoundGameId() != null;
        dto.nickname = entry.getNickname();
        dto.remark = entry.getRemark();
        dto.status = entry.getStatus();
        dto.expireAt = entry.getExpireAt() == null ? null : entry.getExpireAt().format(DATE_TIME);
        dto.expireDate = entry.getExpireAt() == null ? null : entry.getExpireAt().format(DATE);
        dto.active = active;
        dto.createdAt = entry.getCreatedAt() == null ? null : entry.getCreatedAt().format(DATE_TIME);
        dto.updatedAt = entry.getUpdatedAt() == null ? null : entry.getUpdatedAt().format(DATE_TIME);
        dto.createdBy = entry.getCreatedBy();
        return dto;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getQq() { return qq; }
    public void setQq(String qq) { this.qq = qq; }
    public String getMcId() { return mcId; }
    public void setMcId(String mcId) { this.mcId = mcId; }
    public Boolean getGameIdBound() { return gameIdBound; }
    public void setGameIdBound(Boolean gameIdBound) { this.gameIdBound = gameIdBound; }
    public String getNickname() { return nickname; }
    public void setNickname(String nickname) { this.nickname = nickname; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }
    public String getExpireAt() { return expireAt; }
    public void setExpireAt(String expireAt) { this.expireAt = expireAt; }
    public String getExpireDate() { return expireDate; }
    public void setExpireDate(String expireDate) { this.expireDate = expireDate; }
    public Boolean getActive() { return active; }
    public void setActive(Boolean active) { this.active = active; }
    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
    public String getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(String updatedAt) { this.updatedAt = updatedAt; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
}
