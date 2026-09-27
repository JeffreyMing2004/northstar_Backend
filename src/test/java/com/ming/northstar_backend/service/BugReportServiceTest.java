package com.ming.northstar_backend.service;

import com.ming.northstar_backend.entity.BugReport;
import com.ming.northstar_backend.repository.BugReportRepository;
import com.ming.northstar_backend.repository.UserRepository;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BugReportServiceTest {

    private final BugReportRepository bugReportRepository = mock(BugReportRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final BugReportService service = new BugReportService(bugReportRepository, userRepository);

    @Test
    void exportsCsvWithBomHeaderAndEscapedFields() {
        BugReport report = new BugReport();
        report.setId(1L);
        report.setReportNo("BUG-00000001");
        report.setUsername("steve");
        report.setQq("123456");
        report.setCategory("对局内bug");
        report.setDescription("第一步正常, 然后\"卡死\"\n第二行");
        report.setStatus("pending");
        report.setCreatedAt(LocalDateTime.of(2026, 9, 27, 10, 30, 0));
        when(bugReportRepository.findAllByOrderByCreatedAtDesc()).thenReturn(List.of(report));

        String csv = service.exportCsv();

        assertTrue(csv.startsWith("\uFEFF"), "CSV 开头应有 UTF-8 BOM");
        assertTrue(csv.contains("提交编号,用户名,QQ,问题分类,问题描述,状态,管理员备注,处理人,处理时间,提交时间"));
        assertTrue(csv.contains("\"第一步正常, 然后\"\"卡死\"\"\n第二行\""), "含逗号、引号、换行的字段应按 RFC 4180 转义");
        assertTrue(csv.contains("2026-09-27 10:30:00"));
    }

    @Test
    void prefixesFormulaLeadingFieldsWithSingleQuote() {
        BugReport report = new BugReport();
        report.setUsername("alex");
        report.setCategory("人机bug");
        report.setDescription("=HYPERLINK(\"http://evil.example\")");
        report.setCreatedAt(LocalDateTime.now());
        when(bugReportRepository.findAllByOrderByCreatedAtDesc()).thenReturn(List.of(report));

        String csv = service.exportCsv();

        assertTrue(csv.contains("'=HYPERLINK"), "以公式字符开头的字段应加单引号前缀防注入");
    }
}
