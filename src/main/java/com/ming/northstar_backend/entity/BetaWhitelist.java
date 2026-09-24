package com.ming.northstar_backend.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * 北极战区内测白名单。
 *
 * <p>一条记录 = 「QQ + 游戏ID」的一次授权，供「客户端 Mod 内测资格校验接口」查询：
 * {@code GET /api/beta/verify?qq=&name=}。</p>
 *
 * <p><b>为什么没有 UUID</b>：北极战区是离线模式服务器，玩家 UUID 由各启动器自行生成、
 * 每次启动都可能不同，服务端也无法据此识别玩家，因此完全排除在校验之外。
 * 身份只由 QQ 与游戏 ID（Minecraft ID）两项组成。</p>
 *
 * <p>{@code mcId} 为空表示「只绑 QQ，不限定游戏ID」——该 QQ 用任意游戏 ID 都能通过；
 * 填了则必须与客户端上报的名字一致（忽略大小写）。是否允许空绑定由配置项
 * {@code northstar.verify.require-mc-id} 决定。</p>
 *
 * <p>唯一键是 {@code (qq, mcId)} 组合：同一个 QQ 可以绑定多个游戏 ID（例如小号）。</p>
 *
 * <p>与 {@link BetaApplication} 的关系：申请/审批流程记录「谁申请过什么内测计划」，
 * 本表只记录「该 QQ（+游戏ID）现在是否被允许进入客户端」，两者互不覆盖。</p>
 */
@Entity
@Table(name = "northstar_beta_whitelist",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_beta_whitelist_qq_mcid", columnNames = {"qq", "mcId"}))
public class BetaWhitelist {

    /** 状态：启用。 */
    public static final int STATUS_ENABLED = 1;
    /** 状态：禁用。 */
    public static final int STATUS_DISABLED = 0;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** QQ 号，纯数字。 */
    @Column(nullable = false, length = 16)
    private String qq;

    /** 绑定的游戏 ID（Minecraft ID）。为空表示不限定游戏 ID。 */
    @Column(length = 64)
    private String mcId;

    /** 昵称 / 备注名，仅用于后台识别，不参与校验。 */
    @Column(length = 64)
    private String nickname;

    /** 后台备注。 */
    @Column(length = 255)
    private String remark;

    /** 1 启用、0 禁用。 */
    @Column(nullable = false)
    private Integer status = STATUS_ENABLED;

    /** 过期时间，null 表示永不过期。 */
    private LocalDateTime expireAt;

    private LocalDateTime createdAt = LocalDateTime.now();

    private LocalDateTime updatedAt = LocalDateTime.now();

    /** 操作人。 */
    @Column(length = 64)
    private String createdBy;

    public BetaWhitelist() {
    }

    /** 该条目当前是否处于「可用」状态（启用且未过期）。 */
    public boolean isUsable(LocalDateTime now) {
        if (status == null || status != STATUS_ENABLED) {
            return false;
        }
        return expireAt == null || expireAt.isAfter(now);
    }

    /** 绑定的游戏 ID，未绑定时返回 null（而不是空串，便于直接比较）。 */
    public String getBoundGameId() {
        return mcId == null || mcId.isBlank() ? null : mcId.trim();
    }

    /** 该条目的绑定是否与上报的游戏 ID 匹配（忽略大小写）。 */
    public boolean matchesGameId(String playerName) {
        String bound = getBoundGameId();
        return bound != null && playerName != null && bound.equalsIgnoreCase(playerName);
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
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
    public LocalDateTime getExpireAt() { return expireAt; }
    public void setExpireAt(LocalDateTime expireAt) { this.expireAt = expireAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
}
