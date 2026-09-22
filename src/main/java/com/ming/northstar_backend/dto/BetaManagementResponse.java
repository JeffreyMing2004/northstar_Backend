package com.ming.northstar_backend.dto;

public class BetaManagementResponse {
    private BetaApplicationDto application;
    private boolean emailSent;

    public BetaManagementResponse() {}

    public BetaManagementResponse(BetaApplicationDto application, boolean emailSent) {
        this.application = application;
        this.emailSent = emailSent;
    }

    public BetaApplicationDto getApplication() { return application; }
    public void setApplication(BetaApplicationDto application) { this.application = application; }
    public boolean isEmailSent() { return emailSent; }
    public void setEmailSent(boolean emailSent) { this.emailSent = emailSent; }
}
