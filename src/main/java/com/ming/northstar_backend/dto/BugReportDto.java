package com.ming.northstar_backend.dto;

import com.ming.northstar_backend.entity.BugReport;

import java.time.LocalDateTime;

public class BugReportDto {
    private Long id;
    private String username;
    private String qq;
    private String category;
    private String description;
    private String reportNo;
    private String status;
    private String adminNote;
    private LocalDateTime processedAt;
    private String processedBy;
    private LocalDateTime createdAt;

    public BugReportDto() {}

    public static BugReportDto from(BugReport r) {
        BugReportDto dto = new BugReportDto();
        dto.id = r.getId();
        dto.username = r.getUsername();
        dto.qq = r.getQq();
        dto.category = r.getCategory();
        dto.description = r.getDescription();
        dto.reportNo = r.getReportNo();
        dto.status = r.getStatus();
        dto.adminNote = r.getAdminNote();
        dto.processedAt = r.getProcessedAt();
        dto.processedBy = r.getProcessedBy();
        dto.createdAt = r.getCreatedAt();
        return dto;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
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
