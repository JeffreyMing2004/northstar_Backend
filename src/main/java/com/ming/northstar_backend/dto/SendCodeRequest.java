package com.ming.northstar_backend.dto;

public class SendCodeRequest {
    private String email;
    /** 发码场景：{@code register} 注册 / {@code reset} 重置密码，缺省按注册处理。 */
    private String purpose;

    public SendCodeRequest() {}

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getPurpose() { return purpose; }
    public void setPurpose(String purpose) { this.purpose = purpose; }
}
