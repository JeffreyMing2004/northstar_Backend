package com.ming.northstar_backend.repository;

import com.ming.northstar_backend.entity.BetaApplication;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface BetaApplicationRepository extends JpaRepository<BetaApplication, Long> {
    List<BetaApplication> findByUserIdOrderByCreatedAtDesc(Long userId);
    boolean existsByUserIdAndStatus(Long userId, String status);
    BetaApplication findFirstByUserIdAndStatusOrderByCreatedAtDesc(Long userId, String status);
    List<BetaApplication> findByStatusAndUserIdIsNull(String status);
    List<BetaApplication> findAllByOrderByCreatedAtDesc();
    List<BetaApplication> findByStatusOrderByCreatedAtDesc(String status);
    long countByStatus(String status);
    long countByPlanIdAndStatus(Long planId, String status);
    void deleteByUserId(Long userId);
}
