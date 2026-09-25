package com.ming.northstar_backend.repository;

import com.ming.northstar_backend.entity.BugReport;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BugReportRepository extends JpaRepository<BugReport, Long> {

    List<BugReport> findAllByOrderByCreatedAtDesc();
}
