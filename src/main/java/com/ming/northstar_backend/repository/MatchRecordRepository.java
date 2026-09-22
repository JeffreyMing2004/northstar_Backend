package com.ming.northstar_backend.repository;

import com.ming.northstar_backend.entity.MatchRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface MatchRecordRepository extends JpaRepository<MatchRecord, Long> {
    List<MatchRecord> findByUserIdOrderByPlayedAtDesc(Long userId);
    List<MatchRecord> findTop8ByUserIdOrderByPlayedAtDesc(Long userId);
    long countByUserId(Long userId);
    long countByUserIdAndWin(Long userId, Boolean win);

    @Query("SELECT COALESCE(SUM(m.kills), 0) FROM MatchRecord m WHERE m.userId = :userId")
    int sumKillsByUserId(@Param("userId") Long userId);

    @Query("SELECT COALESCE(SUM(m.deaths), 0) FROM MatchRecord m WHERE m.userId = :userId")
    int sumDeathsByUserId(@Param("userId") Long userId);

    @Query("SELECT COALESCE(MAX(m.kills), 0) FROM MatchRecord m WHERE m.userId = :userId")
    int maxKillsByUserId(@Param("userId") Long userId);

    List<MatchRecord> findTop10ByOrderByPlayedAtDesc();
}
