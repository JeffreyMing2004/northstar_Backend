package com.ming.northstar_backend.dto;

import com.ming.northstar_backend.entity.BetaPlan;

import java.time.format.DateTimeFormatter;

public class BetaPlanDto {
    private Long id;
    private String name;
    private String phase;
    private String description;
    private String startsOn;
    private String endsOn;
    private Integer capacity;
    private long approvedCount;
    private String allowedModes;
    private String status;

    public BetaPlanDto() {}

    public static BetaPlanDto from(BetaPlan plan, long approvedCount) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        BetaPlanDto dto = new BetaPlanDto();
        dto.id = plan.getId();
        dto.name = plan.getName();
        dto.phase = plan.getPhase();
        dto.description = plan.getDescription();
        dto.startsOn = plan.getStartsOn() == null ? "" : plan.getStartsOn().format(formatter);
        dto.endsOn = plan.getEndsOn() == null ? "" : plan.getEndsOn().format(formatter);
        dto.capacity = plan.getCapacity();
        dto.approvedCount = approvedCount;
        dto.allowedModes = plan.getAllowedModes();
        dto.status = plan.getStatus();
        return dto;
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public String getPhase() { return phase; }
    public String getDescription() { return description; }
    public String getStartsOn() { return startsOn; }
    public String getEndsOn() { return endsOn; }
    public Integer getCapacity() { return capacity; }
    public long getApprovedCount() { return approvedCount; }
    public String getAllowedModes() { return allowedModes; }
    public String getStatus() { return status; }
}
