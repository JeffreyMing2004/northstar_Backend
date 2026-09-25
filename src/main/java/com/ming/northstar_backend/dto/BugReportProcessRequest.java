package com.ming.northstar_backend.dto;

/**
 * 管理员处理 BUG 反馈的请求体。
 */
public class BugReportProcessRequest {
    /** {@code pending} 待处理 / {@code processed} 已处理。 */
    private String status;

    private String adminNote;

    public BugReportProcessRequest() {}

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getAdminNote() { return adminNote; }
    public void setAdminNote(String adminNote) { this.adminNote = adminNote; }
}
