package com.ming.northstar_backend.dto;

import java.time.LocalDate;

public class BetaPlanRequest {
    private String name;
    private String phase;
    private String description;
    private LocalDate startsOn;
    private LocalDate endsOn;
    private Integer capacity;
    private String allowedModes;

    public BetaPlanRequest() {}

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getPhase() { return phase; }
    public void setPhase(String phase) { this.phase = phase; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public LocalDate getStartsOn() { return startsOn; }
    public void setStartsOn(LocalDate startsOn) { this.startsOn = startsOn; }
    public LocalDate getEndsOn() { return endsOn; }
    public void setEndsOn(LocalDate endsOn) { this.endsOn = endsOn; }
    public Integer getCapacity() { return capacity; }
    public void setCapacity(Integer capacity) { this.capacity = capacity; }
    public String getAllowedModes() { return allowedModes; }
    public void setAllowedModes(String allowedModes) { this.allowedModes = allowedModes; }
}
