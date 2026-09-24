package com.ming.northstar_backend.controller;

import com.ming.northstar_backend.dto.*;
import com.ming.northstar_backend.service.BetaWhitelistService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 接口 B：内测白名单管理（供运营后台使用）。
 *
 * <p>整组接口挂在 {@code /api/admin/**} 下，由 {@code SecurityConfig} 统一要求
 * {@code ROLE_ADMIN}，禁止公网匿名访问。</p>
 */
@RestController
@RequestMapping("/api/admin/beta")
public class AdminBetaWhitelistController {

    private final BetaWhitelistService whitelistService;

    public AdminBetaWhitelistController(BetaWhitelistService whitelistService) {
        this.whitelistService = whitelistService;
    }

    /** 分页查询白名单。 */
    @GetMapping("/whitelist")
    public ResponseEntity<ApiResponse<PageResult<BetaWhitelistDto>>> listWhitelist(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer status) {
        return execute(() -> ApiResponse.ok(
                whitelistService.list(page, size, keyword, status)));
    }

    /** 新增一个 QQ 到白名单。 */
    @PostMapping("/whitelist")
    public ResponseEntity<ApiResponse<BetaWhitelistDto>> createWhitelist(
            Authentication auth, @RequestBody BetaWhitelistRequest request) {
        return execute(() -> ApiResponse.ok(
                "白名单已添加", whitelistService.create(request, operator(auth))));
    }

    /** 修改白名单条目（备注、绑定、启用状态、到期时间等）。 */
    @PutMapping("/whitelist/{id}")
    public ResponseEntity<ApiResponse<BetaWhitelistDto>> updateWhitelist(
            @PathVariable Long id, @RequestBody BetaWhitelistRequest request) {
        return execute(() -> ApiResponse.ok(
                "白名单已更新", whitelistService.update(id, request)));
    }

    /** 删除（剔除资格）。 */
    @DeleteMapping("/whitelist/{id}")
    public ResponseEntity<ApiResponse<String>> deleteWhitelist(@PathVariable Long id) {
        return execute(() -> {
            whitelistService.delete(id);
            return ApiResponse.ok("白名单条目已删除", "ok");
        });
    }

    /**
     * 批量导入（JSON）。
     *
     * <pre>{@code
     * { "replace": false, "items": [ { "qq": "123456789", "mcId": "Steve", "nickname": "明明" } ] }
     * }</pre>
     *
     * <p>{@code mcId} 是绑定的游戏 ID。留空表示该 QQ 不限定游戏 ID（用任意游戏 ID 都能通过）；
     * 填写后必须与玩家实际登录的游戏 ID 一致（忽略大小写）。</p>
     */
    @PostMapping("/whitelist/import")
    public ResponseEntity<ApiResponse<BetaWhitelistImportResult>> importWhitelist(
            Authentication auth, @RequestBody BetaWhitelistImportRequest request) {
        return execute(() -> {
            if (request.getItems() == null || request.getItems().isEmpty()) {
                throw new RuntimeException("导入内容为空");
            }
            BetaWhitelistImportResult result = whitelistService.importBatch(request, operator(auth));
            return ApiResponse.ok(describeImport(result), result);
        });
    }

    /**
     * 批量导入（纯文本，每行 {@code qq,gameId,nickname,remark,expireAt}）。
     *
     * <pre>{@code
     * qq,gameId,nickname,remark,expireAt
     * 123456789,Steve,明明,一期内测,2026-12-31
     * 987654321,,老王,只绑QQ不限定游戏ID
     * }</pre>
     *
     * <p>请求体直接传原始文本（{@code Content-Type: text/plain;charset=UTF-8}），
     * 支持逗号与制表符分隔、{@code #} 注释行、以及首行 {@code qq,...} 表头。
     * 这里直接读原始输入流，因此不受消息转换器对 Content-Type 的限制。</p>
     *
     * <p>一行出错（如游戏ID 格式非法）只计入失败明细，不影响其它行。</p>
     *
     * <p>查询参数 {@code replace=true} 会先清空现有白名单（危险，默认 false）。</p>
     */
    @PostMapping("/whitelist/import-csv")
    public ResponseEntity<ApiResponse<BetaWhitelistImportResult>> importWhitelistCsv(
            Authentication auth,
            @RequestParam(defaultValue = "false") boolean replace,
            HttpServletRequest servletRequest) {
        String text;
        try {
            text = new String(servletRequest.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(400, "读取导入内容失败"));
        }
        return execute(() -> {
            if (text.isBlank()) {
                throw new RuntimeException("导入内容为空");
            }
            BetaWhitelistImportResult result = whitelistService.importCsv(text, replace, operator(auth));
            return ApiResponse.ok(describeImport(result), result);
        });
    }

    /** 导出全部白名单为 CSV（带 UTF-8 BOM，Excel 直接双击不乱码）。 */
    @GetMapping("/whitelist/export")
    public ResponseEntity<byte[]> exportWhitelist() {
        byte[] bom = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
        byte[] body = whitelistService.exportCsv().getBytes(StandardCharsets.UTF_8);
        byte[] payload = new byte[bom.length + body.length];
        System.arraycopy(bom, 0, payload, 0, bom.length);
        System.arraycopy(body, 0, payload, bom.length, body.length);

        String filename = "northstar-beta-whitelist-"
                + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")) + ".csv";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(payload);
    }

    /**
     * 查询校验日志（最近 1000 条范围内过滤）。
     *
     * @param showQq true 时返回完整 QQ 号，默认脱敏为 {@code 123****89}
     */
    @GetMapping("/verify-logs")
    public ResponseEntity<ApiResponse<PageResult<BetaVerifyLogDto>>> listVerifyLogs(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String result,
            @RequestParam(defaultValue = "false") boolean showQq) {
        return execute(() -> ApiResponse.ok(
                whitelistService.listLogs(page, size, keyword, result, !showQq)));
    }

    private String describeImport(BetaWhitelistImportResult result) {
        return "导入完成：新增 " + result.getCreated()
                + "，更新 " + result.getUpdated()
                + "，跳过 " + result.getSkipped()
                + "，失败 " + result.getFailed();
    }

    /** 操作人：JwtFilter 把用户名放在 authentication.details 里。 */
    private String operator(Authentication auth) {
        if (auth != null && auth.getDetails() instanceof String username && !username.isBlank()) {
            return username;
        }
        return "admin";
    }

    private <T> ResponseEntity<ApiResponse<T>> execute(AdminAction<T> action) {
        try {
            return ResponseEntity.ok(action.run());
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(400, e.getMessage()));
        }
    }

    @FunctionalInterface
    private interface AdminAction<T> {
        ApiResponse<T> run();
    }
}
