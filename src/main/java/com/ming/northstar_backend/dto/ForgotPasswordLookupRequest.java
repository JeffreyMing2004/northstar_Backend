package com.ming.northstar_backend.dto;

public class ForgotPasswordLookupRequest {
    private String email;

    public ForgotPasswordLookupRequest() {}

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
}
