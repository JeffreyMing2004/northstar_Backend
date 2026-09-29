package com.ming.northstar_backend.dto;

/**
 * 忘记密码第一步的找回结果：把注册邮箱对应的账号身份回显给用户，
 * 由用户确认「这就是我要找回的账号」后再发送验证码。
 */
public class ForgotIdentityDto {
    private String username;
    private String mcId;
    private String email;

    public ForgotIdentityDto() {}

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getMcId() { return mcId; }
    public void setMcId(String mcId) { this.mcId = mcId; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
}
