package com.ming.northstar_backend.service;

import com.ming.northstar_backend.dto.BugReportDto;
import com.ming.northstar_backend.dto.BugReportProcessRequest;
import com.ming.northstar_backend.dto.BugReportRequest;
import com.ming.northstar_backend.entity.BugReport;
import com.ming.northstar_backend.entity.User;
import com.ming.northstar_backend.repository.BugReportRepository;
import com.ming.northstar_backend.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Set;

@Service
public class BugReportService {

    /** 与前端「问题反馈」下拉框保持一致的问题分类。 */
    public static final Set<String> ALLOWED_CATEGORIES = Set.of(
            "战备页bug",
            "战绩显示bug",
            "对局房间bug",
            "对局内bug",
            "胜利条件触发bug",
            "死亡后视角bug",
            "赛后结算bug",
            "所有的按键逻辑bug",
            "闪退及离开服务器后重回对局过程中的bug",
            "观战视角bug",
            "人机bug"
    );

    public static final String STATUS_PENDING = "pending";
    public static final String STATUS_PROCESSED = "processed";

    private static final int MAX_DESCRIPTION_LENGTH = 2000;
    private static final int MAX_USERNAME_LENGTH = 32;
    private static final int MAX_QQ_LENGTH = 16;

    private final BugReportRepository bugReportRepository;
    private final UserRepository userRepository;

    public BugReportService(BugReportRepository bugReportRepository, UserRepository userRepository) {
        this.bugReportRepository = bugReportRepository;
        this.userRepository = userRepository;
    }

    public BugReportDto submit(Long userId, BugReportRequest req) {
        String category = req.getCategory() == null ? "" : req.getCategory().trim();
        String description = req.getDescription() == null ? "" : req.getDescription().trim();

        if (category.isEmpty()) {
            throw new RuntimeException("请选择问题分类");
        }
        if (!ALLOWED_CATEGORIES.contains(category)) {
            throw new RuntimeException("无效的问题分类");
        }
        if (description.isEmpty()) {
            throw new RuntimeException("请填写问题描述");
        }
        if (description.length() > MAX_DESCRIPTION_LENGTH) {
            throw new RuntimeException("问题描述不能超过 " + MAX_DESCRIPTION_LENGTH + " 字");
        }

        String username;
        String qq;
        if (userId != null) {
            // 已登录：身份信息以账号为准，忽略前端传入值，防止冒名提交。
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new RuntimeException("用户不存在"));
            username = user.getUsername();
            qq = user.getQq();
        } else {
            // 未登录：必须手动填写用户名与 QQ 联系方式。
            username = req.getUsername() == null ? "" : req.getUsername().trim();
            qq = req.getQq() == null ? "" : req.getQq().trim();
            if (username.isEmpty()) {
                throw new RuntimeException("请填写用户名");
            }
            if (username.length() > MAX_USERNAME_LENGTH) {
                throw new RuntimeException("用户名不能超过 " + MAX_USERNAME_LENGTH + " 个字符");
            }
            if (qq.isEmpty()) {
                throw new RuntimeException("请填写 QQ 号");
            }
            if (qq.length() > MAX_QQ_LENGTH) {
                throw new RuntimeException("QQ 号不能超过 " + MAX_QQ_LENGTH + " 个字符");
            }
        }

        BugReport report = new BugReport();
        report.setUserId(userId);
        report.setUsername(username);
        report.setQq(qq);
        report.setCategory(category);
        report.setDescription(description);
        // 先落库拿到自增 ID，再据此生成稳定的唯一提交编号
        report = bugReportRepository.save(report);
        report.setReportNo(String.format("BUG-%08d", report.getId()));
        report = bugReportRepository.save(report);

        return BugReportDto.from(report);
    }

    public List<BugReportDto> listAll() {
        return bugReportRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(BugReportDto::from)
                .toList();
    }

    public BugReportDto process(Long id, BugReportProcessRequest req, String adminUsername) {
        String status = req.getStatus() == null ? "" : req.getStatus().trim();
        if (!STATUS_PENDING.equals(status) && !STATUS_PROCESSED.equals(status)) {
            throw new RuntimeException("无效的处理状态");
        }

        BugReport report = bugReportRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("反馈不存在"));

        String adminNote = req.getAdminNote() == null ? "" : req.getAdminNote().trim();
        report.setStatus(status);
        report.setAdminNote(adminNote.isEmpty() ? null : adminNote);
        report.setProcessedBy(adminUsername);
        report.setProcessedAt(STATUS_PROCESSED.equals(status) ? LocalDateTime.now() : null);
        report = bugReportRepository.save(report);

        return BugReportDto.from(report);
    }

    private static final DateTimeFormatter CSV_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * 将全部反馈导出为 CSV 文本，按提交时间倒序。
     *
     * <p>开头带 UTF-8 BOM，保证 Excel 直接双击打开中文不乱码。</p>
     */
    public String exportCsv() {
        StringBuilder csv = new StringBuilder("\uFEFF");
        csv.append("提交编号,用户名,QQ,问题分类,问题描述,状态,管理员备注,处理人,处理时间,提交时间\r\n");
        for (BugReportDto report : listAll()) {
            csv.append(csvField(report.getReportNo())).append(',')
                    .append(csvField(report.getUsername())).append(',')
                    .append(csvField(report.getQq())).append(',')
                    .append(csvField(report.getCategory())).append(',')
                    .append(csvField(report.getDescription())).append(',')
                    .append(csvField(report.getStatus())).append(',')
                    .append(csvField(report.getAdminNote())).append(',')
                    .append(csvField(report.getProcessedBy())).append(',')
                    .append(csvField(formatTime(report.getProcessedAt()))).append(',')
                    .append(csvField(formatTime(report.getCreatedAt())))
                    .append("\r\n");
        }
        return csv.toString();
    }

    private static String formatTime(LocalDateTime time) {
        return time == null ? "" : time.format(CSV_TIME_FORMAT);
    }

    /**
     * 按 RFC 4180 转义单个字段：包含逗号、引号或换行时加引号包裹，内部引号翻倍。
     *
     * <p>对以公式字符开头的内容加单引号前缀，防止导出文件在 Excel 中被当作公式执行（OWASP 建议）。</p>
     */
    private static String csvField(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        String escaped = value;
        if ("=+-@\t\r".indexOf(escaped.charAt(0)) >= 0) {
            escaped = "'" + escaped;
        }
        if (escaped.contains(",") || escaped.contains("\"") || escaped.contains("\n") || escaped.contains("\r")) {
            escaped = '"' + escaped.replace("\"", "\"\"") + '"';
        }
        return escaped;
    }
}