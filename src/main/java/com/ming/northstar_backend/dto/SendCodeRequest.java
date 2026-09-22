package com.ming.northstar_backend.dto;

public class SendCodeRequest {
    private String email;

    public SendCodeRequest() {}

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
}