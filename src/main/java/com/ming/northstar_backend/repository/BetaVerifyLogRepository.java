package com.ming.northstar_backend.repository;

import com.ming.northstar_backend.entity.BetaVerifyLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BetaVerifyLogRepository extends JpaRepository<BetaVerifyLog, Long> {

    /** 后台查询用：只取最近 1000 条（校验日志增长很快，避免全表扫描）。 */
    List<BetaVerifyLog> findTop1000ByOrderByCreatedAtDesc();

    long countByResult(String result);
}
