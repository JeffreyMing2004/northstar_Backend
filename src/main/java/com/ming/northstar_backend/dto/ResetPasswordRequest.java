package com.ming.northstar_backend.dto;

public class ResetPasswordRequest {
    private String email;
    private String emailCode;
    private String newPassword;

    public ResetPasswordRequest() {}

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getEmailCode() { return emailCode; }
    public void setEmailCode(String emailCode) { this.emailCode = emailCode; }
    public String getNewPassword() { return newPassword; }
    public void setNewPassword(String newPassword) { this.newPassword = newPassword; }
}
