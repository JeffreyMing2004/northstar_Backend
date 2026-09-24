package com.ming.northstar_backend.repository;

import com.ming.northstar_backend.entity.AdminGrant;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AdminGrantRepository extends JpaRepository<AdminGrant, Long> {
    Optional<AdminGrant> findByUsernameIgnoreCase(String username);
    boolean existsByUsernameIgnoreCase(String username);
    void deleteByUsernameIgnoreCase(String username);
}
