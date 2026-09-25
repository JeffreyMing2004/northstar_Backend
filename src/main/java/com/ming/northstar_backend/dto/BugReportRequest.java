package com.ming.northstar_backend.dto;

/**
 * 玩家提交 BUG 反馈的请求体。
 *
 * <p>已登录玩家无需传用户名与 QQ：后端从 JWT 反查当前登录用户后自行写入，
 * 避免玩家把反馈冒名提交到别人的账号上；未登录玩家必须手动填写用户名与 QQ。</p>
 */
public class BugReportRequest {
    private String username;
    private String qq;
    private String category;
    private String description;

    public BugReportRequest() {}

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getQq() { return qq; }
    public void setQq(String qq) { this.qq = qq; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
}