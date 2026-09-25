package com.ming.northstar_backend.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * BUG 反馈记录。
 *
 * <p>已登录玩家：用户名与 QQ 从登录账号取值并落库为快照，即使之后改了昵称 /
 * 更正了 QQ，历史反馈仍能对应到提交时的身份；未登录玩家使用其手动填写的用户名与 QQ。</p>
 */
@Entity
@Table(name = "bug_reports")
public class BugReport {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column
    private Long userId;

    @Column(nullable = false, length = 32)
    private String username;

    @Column(length = 16)
    private String qq;

    @Column(nullable = false, length = 64)
    private String category;

    @Column(nullable = false, length = 2000)
    private String description;

    /** 展示给玩家的唯一提交编号，例如 {@code BUG-00000042}。 */
    @Column(unique = true, length = 32)
    private String reportNo;

    /** {@code pending} 待处理 / {@code processed} 已处理。 */
    @Column(nullable = false, length = 16)
    private String status = "pending";

    /** 管理员处理时填写的备注。 */
    @Column(length = 500)
    private String adminNote;

    private LocalDateTime processedAt;

    @Column(length = 32)
    private String processedBy;

    private LocalDateTime createdAt = LocalDateTime.now();

    public BugReport() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getQq() { return qq; }
    public void setQq(String qq) { this.qq = qq; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getReportNo() { return reportNo; }
    public void setReportNo(String reportNo) { this.reportNo = reportNo; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getAdminNote() { return adminNote; }
    public void setAdminNote(String adminNote) { this.adminNote = adminNote; }
    public LocalDateTime getProcessedAt() { return processedAt; }
    public void setProcessedAt(LocalDateTime processedAt) { this.processedAt = processedAt; }
    public String getProcessedBy() { return processedBy; }
    public void setProcessedBy(String processedBy) { this.processedBy = processedBy; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
