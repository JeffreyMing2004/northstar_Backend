package com.ming.northstar_backend.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "admin_grants", uniqueConstraints = {
    @UniqueConstraint(name = "uk_admin_grants_username", columnNames = {"username"})
})
public class AdminGrant {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 32)
    private String username;

    private LocalDateTime createdAt = LocalDateTime.now();

    public AdminGrant() {}

    public AdminGrant(String username) {
        this.username = username;
    }

    public Long getId() { return id; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
