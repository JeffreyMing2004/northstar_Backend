package com.ming.northstar_backend.dto;

public class MinecraftAvatarDto {
    private String playerId;
    private Boolean premium;
    private String avatarUrl;
    private String fallback;

    public MinecraftAvatarDto() {}

    public MinecraftAvatarDto(String playerId, Boolean premium, String avatarUrl, String fallback) {
        this.playerId = playerId;
        this.premium = premium;
        this.avatarUrl = avatarUrl;
        this.fallback = fallback;
    }

    public String getPlayerId() { return playerId; }
    public void setPlayerId(String playerId) { this.playerId = playerId; }
    public Boolean getPremium() { return premium; }
    public void setPremium(Boolean premium) { this.premium = premium; }
    public String getAvatarUrl() { return avatarUrl; }
    public void setAvatarUrl(String avatarUrl) { this.avatarUrl = avatarUrl; }
    public String getFallback() { return fallback; }
    public void setFallback(String fallback) { this.fallback = fallback; }
}
