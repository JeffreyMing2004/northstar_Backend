package com.ming.northstar_backend.dto;

public class RegisterRequest {
    private String username;
    private String password;
    private String email;
    private String emailCode;
    /** 玩家 QQ 号，内测资格校验的键之一。 */
    private String qq;
    private String mcId;

    public RegisterRequest() {}

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getEmailCode() { return emailCode; }
    public void setEmailCode(String emailCode) { this.emailCode = emailCode; }
    public String getQq() { return qq; }
    public void setQq(String qq) { this.qq = qq; }
    public String getMcId() { return mcId; }
    public void setMcId(String mcId) { this.mcId = mcId; }
}