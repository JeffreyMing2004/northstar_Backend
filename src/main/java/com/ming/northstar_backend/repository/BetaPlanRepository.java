package com.ming.northstar_backend.repository;

import com.ming.northstar_backend.entity.BetaPlan;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BetaPlanRepository extends JpaRepository<BetaPlan, Long> {
    List<BetaPlan> findAllByOrderByStartsOnAsc();
    List<BetaPlan> findByStatusInOrderByStartsOnAsc(List<String> statuses);
    List<BetaPlan> findByStatusOrderByStartsOnAsc(String status);
}
