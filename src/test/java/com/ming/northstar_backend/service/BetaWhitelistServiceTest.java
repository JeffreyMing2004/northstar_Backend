package com.ming.northstar_backend.service;

import com.ming.northstar_backend.dto.*;
import com.ming.northstar_backend.entity.BetaVerifyLog;
import com.ming.northstar_backend.entity.BetaWhitelist;
import com.ming.northstar_backend.repository.BetaVerifyLogRepository;
import com.ming.northstar_backend.repository.BetaWhitelistRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * 内测白名单服务测试。
 *
 * <p>重点覆盖两件事：</p>
 * <ol>
 *   <li><b>身份判定契约</b>：身份只由「QQ + 游戏ID」决定，离线模式下不使用 UUID</li>
 *   <li><b>客户端 Mod 判定契约</b>：HTTP 状态码与 {@code success}/{@code code}
 *       的组合必须与 {@code RemoteVerifier.readSuccessFlag} 完全一致，
 *       否则玩家会被误判崩溃</li>
 * </ol>
 */
class BetaWhitelistServiceTest {

    private static final String QQ_PATTERN = "^[1-9]\\d{4,10}$";

    private BetaWhitelistRepository whitelistRepo;
    private BetaVerifyLogRepository logRepo;
    private BetaWhitelistService service;

    @BeforeEach
    void setUp() {
        whitelistRepo = mock(BetaWhitelistRepository.class);
        logRepo = mock(BetaVerifyLogRepository.class);
        service = new BetaWhitelistService(whitelistRepo, logRepo, QQ_PATTERN, 60, false);
    }

    // ---------------- 接口 A：身份判定（QQ + 游戏ID） ----------------

    @Test
    void passesWhenQqIsWhitelistedAndGameIdMatchesTheBinding() {
        stubQq("123456789", entry("123456789", "Steve", 1, null));

        BetaWhitelistService.VerifyOutcome outcome = verifyAs("123456789", "Steve");

        assertEquals(200, outcome.httpStatus());
        assertTrue(outcome.body().isSuccess());
        assertEquals(0, outcome.body().getCode());
        assertEquals("123456789", outcome.body().getData().getQq());
        assertEquals("Steve", outcome.body().getData().getGameId());
        assertEquals(BetaVerifyLog.MATCH_BOUND, outcome.body().getData().getMatchedBy());
        verify(logRepo).save(argThat(log -> BetaVerifyLog.RESULT_PASS.equals(log.getResult())
                && BetaVerifyLog.MATCH_BOUND.equals(log.getMatchedBy())));
    }

    @Test
    void gameIdComparisonIgnoresCase() {
        stubQq("123456789", entry("123456789", "Steve", 1, null));

        BetaWhitelistService.VerifyOutcome outcome = verifyAs("123456789", "steve");

        assertTrue(outcome.body().isSuccess(), "游戏ID 比较应忽略大小写");
        assertEquals(BetaVerifyLog.MATCH_BOUND, outcome.body().getData().getMatchedBy());
        assertEquals("Steve", outcome.body().getData().getGameId());
    }

    @Test
    void rejectsWhenGameIdDiffersFromTheBinding() {
        // 关键场景：QQ 在名单里，但换了个游戏ID 进来 -> 必须拒绝（客户端会崩溃退出）
        stubQq("123456789", entry("123456789", "Steve", 1, null));

        BetaWhitelistService.VerifyOutcome outcome = verifyAs("123456789", "Alex");

        assertEquals(200, outcome.httpStatus());
        assertFalse(outcome.body().isSuccess(), "游戏ID 不匹配必须判为未通过");
        assertEquals(1001, outcome.body().getCode());
        assertNull(outcome.body().getData());
        verify(logRepo).save(argThat(log -> BetaVerifyLog.RESULT_REJECT.equals(log.getResult())
                && log.getReason() != null && log.getReason().contains("游戏ID 与绑定不一致")));
    }

    @Test
    void passesUnboundQqWithAnyGameIdByDefault() {
        // mcId 留空 = 只绑 QQ、不限定游戏ID
        stubQq("123456789", entry("123456789", null, 1, null));

        BetaWhitelistService.VerifyOutcome outcome = verifyAs("123456789", "任意名字");

        assertEquals(200, outcome.httpStatus());
        assertTrue(outcome.body().isSuccess());
        assertNull(outcome.body().getData().getGameId());
        assertEquals(BetaVerifyLog.MATCH_UNBOUND, outcome.body().getData().getMatchedBy());
    }

    @Test
    void rejectsUnboundQqWhenRequireGameIdIsEnabled() {
        BetaWhitelistService strict = new BetaWhitelistService(whitelistRepo, logRepo, QQ_PATTERN, 60, true);
        stubQq("123456789", entry("123456789", null, 1, null));

        BetaWhitelistService.VerifyOutcome outcome = strict.verify("123456789", "Steve", "1.2.3.4", "UA");

        assertEquals(200, outcome.httpStatus());
        assertFalse(outcome.body().isSuccess(), "开启 require-mc-id 后未绑定游戏ID 必须拒绝");
        assertEquals(1001, outcome.body().getCode());
    }

    @Test
    void picksTheRowThatMatchesTheReportedGameIdAmongSeveral() {
        // 同一 QQ 绑了两个游戏ID：只应命中与上报一致的那条
        stubQq("123456789",
                entry("123456789", "Steve", 1, null),
                entry("123456789", "Alex", 1, null));

        BetaWhitelistService.VerifyOutcome alex = verifyAs("123456789", "Alex");
        assertTrue(alex.body().isSuccess());
        assertEquals("Alex", alex.body().getData().getGameId());

        assertFalse(verifyAs("123456789", "Notch").body().isSuccess(),
                "两个绑定都不匹配时必须拒绝");
    }

    @Test
    void rejectsQqMissingFromWhitelist() {
        BetaWhitelistService.VerifyOutcome outcome = verifyAs("123450000", "Steve");

        assertEquals(200, outcome.httpStatus());
        assertFalse(outcome.body().isSuccess());
        assertEquals(1001, outcome.body().getCode());
        assertNull(outcome.body().getData());
        verify(logRepo).save(argThat(log -> BetaVerifyLog.RESULT_REJECT.equals(log.getResult())));
    }

    @Test
    void rejectsDisabledEntry() {
        stubQq("123456789", entry("123456789", "Steve", 0, null));

        BetaWhitelistService.VerifyOutcome outcome = verifyAs("123456789", "Steve");

        assertEquals(200, outcome.httpStatus());
        assertFalse(outcome.body().isSuccess());
        assertEquals(1001, outcome.body().getCode());
    }

    @Test
    void rejectsExpiredEntry() {
        stubQq("123456789", entry("123456789", "Steve", 1, LocalDateTime.now().minusDays(1)));

        BetaWhitelistService.VerifyOutcome outcome = verifyAs("123456789", "Steve");

        assertEquals(200, outcome.httpStatus());
        assertFalse(outcome.body().isSuccess());
    }

    @Test
    void acceptsEntryThatExpiresInTheFuture() {
        stubQq("123456789", entry("123456789", "Steve", 1, LocalDateTime.now().plusDays(30)));

        BetaWhitelistService.VerifyOutcome outcome = verifyAs("123456789", "Steve");

        assertTrue(outcome.body().isSuccess());
        assertNotNull(outcome.body().getData().getExpire());
    }

    @Test
    void expiredRowDoesNotBlockAnActiveRowForTheSameQq() {
        stubQq("123456789",
                entry("123456789", "Old", 1, LocalDateTime.now().minusDays(1)),
                entry("123456789", "Steve", 1, null));

        assertTrue(verifyAs("123456789", "Steve").body().isSuccess());
        assertFalse(verifyAs("123456789", "Old").body().isSuccess(), "过期条目不应再放行");
    }

    @Test
    void malformedQqYieldsHttp400SoClientCanRetry() {
        BetaWhitelistService.VerifyOutcome outcome = verifyAs("abc123", "Steve");

        assertEquals(400, outcome.httpStatus());
        assertFalse(outcome.body().isSuccess());
        assertEquals(4000, outcome.body().getCode());
        verify(whitelistRepo, never()).findAllByQqOrderByCreatedAtAsc(anyString());
    }

    @Test
    void missingGameIdYieldsHttp400SoClientCanRetry() {
        // 离线模式下 name 是唯一的身份依据之一，缺失只能当作可重试的参数错误；
        // 绝不能判成「未通过」，否则客户端会直接崩溃。
        BetaWhitelistService.VerifyOutcome outcome = verifyAs("123456789", "   ");

        assertEquals(400, outcome.httpStatus());
        assertEquals(4000, outcome.body().getCode());
        verify(whitelistRepo, never()).findAllByQqOrderByCreatedAtAsc(anyString());
    }

    @Test
    void repositoryFailureYieldsHttp500NotReject() {
        when(whitelistRepo.findAllByQqOrderByCreatedAtAsc(anyString()))
                .thenThrow(new RuntimeException("db down"));

        BetaWhitelistService.VerifyOutcome outcome = verifyAs("123456789", "Steve");

        // 关键：必须是 5xx（服务不可用），客户端按可重试处理，不会崩溃
        assertEquals(500, outcome.httpStatus());
        assertFalse(outcome.body().isSuccess());
        assertEquals(5000, outcome.body().getCode());
    }

    @Test
    void rateLimitsPerIp() {
        BetaWhitelistService limited = new BetaWhitelistService(whitelistRepo, logRepo, QQ_PATTERN, 2, false);
        stubQq("123456789", entry("123456789", "Steve", 1, null));

        assertEquals(200, limited.verify("123456789", "Steve", "9.9.9.9", "UA").httpStatus());
        assertEquals(200, limited.verify("123456789", "Steve", "9.9.9.9", "UA").httpStatus());
        BetaWhitelistService.VerifyOutcome third = limited.verify("123456789", "Steve", "9.9.9.9", "UA");

        assertEquals(429, third.httpStatus());
        // 另一个 IP 不受影响
        assertEquals(200, limited.verify("123456789", "Steve", "8.8.8.8", "UA").httpStatus());
    }

    // ---------------- 接口 B：管理 ----------------

    @Test
    void createsEntryAndRejectsDuplicatedPair() {
        BetaWhitelist existing = entry("123456789", "Steve", 1, null);
        when(whitelistRepo.findAllByQqOrderByCreatedAtAsc("123456789"))
                .thenReturn(List.of(), List.of(existing));
        when(whitelistRepo.save(any(BetaWhitelist.class))).thenAnswer(inv -> inv.getArgument(0));

        BetaWhitelistRequest request = new BetaWhitelistRequest();
        request.setQq("123456789");
        request.setMcId("Steve");
        request.setNickname("明明");
        request.setRemark("一期内测");

        BetaWhitelistDto created = service.create(request, "JeffreyMing");
        assertEquals("123456789", created.getQq());
        assertEquals("Steve", created.getMcId());
        assertEquals(Boolean.TRUE, created.getGameIdBound());
        assertEquals(1, created.getStatus());
        assertEquals(Boolean.TRUE, created.getActive());
        assertEquals("JeffreyMing", created.getCreatedBy());

        RuntimeException duplicate = assertThrows(RuntimeException.class,
                () -> service.create(request, "JeffreyMing"));
        assertEquals("该 QQ 已绑定游戏ID「Steve」", duplicate.getMessage());
    }

    @Test
    void sameQqMayBindSeveralGameIdsButOnlyOnceEach() {
        when(whitelistRepo.findAllByQqOrderByCreatedAtAsc("123456789"))
                .thenReturn(List.of(entry("123456789", "Steve", 1, null)));
        when(whitelistRepo.save(any(BetaWhitelist.class))).thenAnswer(inv -> inv.getArgument(0));

        // 同一个 QQ 绑另一个游戏ID -> 允许
        BetaWhitelistRequest second = new BetaWhitelistRequest();
        second.setQq("123456789");
        second.setMcId("Alex");
        assertEquals("Alex", service.create(second, "admin").getMcId());

        // 但绑同一个（忽略大小写）-> 拒绝
        BetaWhitelistRequest same = new BetaWhitelistRequest();
        same.setQq("123456789");
        same.setMcId("steve");
        assertEquals("该 QQ 已绑定游戏ID「steve」",
                assertThrows(RuntimeException.class, () -> service.create(same, "admin")).getMessage());
    }

    @Test
    void rejectsInvalidQqGameIdAndStatusOnCreate() {
        BetaWhitelistRequest badQq = new BetaWhitelistRequest();
        badQq.setQq("0abc");
        assertEquals("QQ 号格式无效",
                assertThrows(RuntimeException.class, () -> service.create(badQq, "admin")).getMessage());

        BetaWhitelistRequest badStatus = new BetaWhitelistRequest();
        badStatus.setQq("123456789");
        badStatus.setStatus(7);
        assertEquals("状态只能为 1（启用）或 0（禁用）",
                assertThrows(RuntimeException.class, () -> service.create(badStatus, "admin")).getMessage());

        BetaWhitelistRequest badGameId = new BetaWhitelistRequest();
        badGameId.setQq("123456789");
        badGameId.setMcId("小明");
        assertEquals("游戏ID 格式无效（3-16 位字母、数字或下划线）",
                assertThrows(RuntimeException.class, () -> service.create(badGameId, "admin")).getMessage());
    }

    @Test
    void updateOnlyTouchesProvidedFields() {
        BetaWhitelist existing = entry("123456789", "Steve", 1, null);
        existing.setId(7L);
        existing.setNickname("旧昵称");
        when(whitelistRepo.findById(7L)).thenReturn(Optional.of(existing));
        when(whitelistRepo.save(any(BetaWhitelist.class))).thenAnswer(inv -> inv.getArgument(0));

        BetaWhitelistRequest request = new BetaWhitelistRequest();
        request.setStatus(0);

        BetaWhitelistDto updated = service.update(7L, request);

        assertEquals(0, updated.getStatus());
        assertEquals("旧昵称", updated.getNickname());
        assertEquals("Steve", updated.getMcId(), "未传 mcId 时不应改动绑定");
        assertEquals(Boolean.FALSE, updated.getActive());
    }

    @Test
    void parsesExpireAtInSeveralFormats() {
        when(whitelistRepo.save(any(BetaWhitelist.class))).thenAnswer(inv -> inv.getArgument(0));

        assertEquals("2026-12-31 23:59", createWithExpire("2026-12-31").getExpireAt());
        assertEquals("2026-12-31 08:30", createWithExpire("2026-12-31 08:30").getExpireAt());
        assertNull(createWithExpire("").getExpireAt());

        BetaWhitelistRequest bad = new BetaWhitelistRequest();
        bad.setQq("123456789");
        bad.setExpireAt("明年");
        assertTrue(assertThrows(RuntimeException.class, () -> service.create(bad, "admin"))
                .getMessage().contains("到期时间格式无效"));
    }

    @Test
    void importsJsonBatchCountingCreatedUpdatedAndFailed() {
        when(whitelistRepo.findAllByQqOrderByCreatedAtAsc("123456789"))
                .thenReturn(List.of(entry("123456789", null, 1, null)));
        when(whitelistRepo.save(any(BetaWhitelist.class))).thenAnswer(inv -> inv.getArgument(0));

        BetaWhitelistImportRequest request = new BetaWhitelistImportRequest();
        request.setItems(List.of(
                item("123456789"), item("987654321"), item("abc"), item("")));

        BetaWhitelistImportResult result = service.importBatch(request, "admin");

        assertEquals(1, result.getCreated());
        assertEquals(1, result.getUpdated());
        assertEquals(1, result.getSkipped());
        assertEquals(1, result.getFailed());
        assertEquals(1, result.getErrors().size());
    }

    @Test
    void importsCsvTextSkippingHeaderCommentsAndBlankLines() {
        when(whitelistRepo.save(any(BetaWhitelist.class))).thenAnswer(inv -> inv.getArgument(0));

        String csv = String.join("\n",
                "qq,gameId,nickname,remark,expireAt",
                "# 一期内测名单",
                "123456789,Steve,明明,北极战区一期内测,2026-12-31",
                "",
                "987654321\t\t老王\t二期内测·只绑QQ,");

        BetaWhitelistImportResult result = service.importCsv(csv, false, "admin");

        assertEquals(2, result.getCreated());
        assertEquals(0, result.getFailed());
        verify(whitelistRepo).save(argThat(entry ->
                "123456789".equals(entry.getQq())
                        && "Steve".equals(entry.getMcId())
                        && "明明".equals(entry.getNickname())
                        && entry.getExpireAt() != null));
        // 第二行 gameId 留空 -> 不限定游戏ID
        verify(whitelistRepo).save(argThat(entry ->
                "987654321".equals(entry.getQq()) && entry.getMcId() == null));
    }

    @Test
    void csvImportReportsBadGameIdWithoutAbortingTheWholeFile() {
        when(whitelistRepo.save(any(BetaWhitelist.class))).thenAnswer(inv -> inv.getArgument(0));

        BetaWhitelistImportResult result = service.importCsv(String.join("\n",
                "123456789,小明,明明,游戏ID 非法",
                "987654321,Steve,老王,正常"), false, "admin");

        assertEquals(1, result.getCreated());
        assertEquals(1, result.getFailed());
        assertTrue(result.getErrors().get(0).contains("游戏ID 格式无效"));
    }

    @Test
    void exportsCsvWithHeaderAndQuotesFieldsContainingCommas() {
        BetaWhitelist entry = entry("123456789", "Steve", 1, null);
        entry.setNickname("明明,老师");
        when(whitelistRepo.findAllByOrderByCreatedAtDesc()).thenReturn(List.of(entry));

        String csv = service.exportCsv();
        String[] lines = csv.split("\n");

        assertEquals("qq,gameId,nickname,gameIdBound,status,active,remark,expireAt,createdAt,createdBy",
                lines[0], "表头必须与文档 §4 一致");
        assertEquals(2, lines.length, "表头 + 一行数据");
        assertTrue(csv.contains("\"明明,老师\""), "含逗号的字段必须加引号");
        assertTrue(lines[1].startsWith("123456789,Steve,\"明明,老师\",1,"),
                "qq/gameId/nickname/gameIdBound 四列顺序错误，实际：" + lines[1]);
    }

    @Test
    void exportsUnboundEntryWithGameIdBoundZero() {
        BetaWhitelist entry = entry("123456789", null, 1, null);
        when(whitelistRepo.findAllByOrderByCreatedAtDesc()).thenReturn(List.of(entry));

        String[] lines = service.exportCsv().split("\n");
        String[] header = lines[0].split(",", -1);
        // 本行没有含逗号的字段，可以直接按逗号切分
        String[] row = lines[1].split(",", -1);

        assertEquals("", row[indexOf(header, "gameId")], "未绑定游戏ID 时该列为空");
        assertEquals("0", row[indexOf(header, "gameIdBound")], "未绑定游戏ID 时 gameIdBound 应为 0");
    }

    private static int indexOf(String[] header, String column) {
        int index = Arrays.asList(header).indexOf(column);
        assertTrue(index >= 0, "导出 CSV 缺少列：" + column);
        return index;
    }

    @Test
    void listsWithKeywordStatusFilterAndPaging() {
        BetaWhitelist a = entry("123456789", "Steve", 1, null);
        BetaWhitelist b = entry("987654321", "Alex", 0, null);
        when(whitelistRepo.findAllByOrderByCreatedAtDesc()).thenReturn(List.of(a, b));

        assertEquals(2, service.list(1, 20, null, null).getTotal());
        assertEquals(1, service.list(1, 20, null, 0).getTotal());
        assertEquals(1, service.list(1, 20, "9876", null).getTotal());
        assertEquals(1, service.list(1, 20, "alex", null).getTotal(), "关键词应能匹配游戏ID");
        assertEquals(0, service.list(1, 20, "不存在", null).getTotal());

        PageResult<BetaWhitelistDto> page = service.list(2, 1, null, null);
        assertEquals(2, page.getTotal());
        assertEquals(1, page.getList().size());
        assertEquals("987654321", page.getList().get(0).getQq());
    }

    @Test
    void masksAndFiltersVerifyLogs() {
        BetaVerifyLog log = new BetaVerifyLog();
        log.setQq("123456789");
        log.setPlayerName("Steve");
        log.setResult(BetaVerifyLog.RESULT_REJECT);
        when(logRepo.findTop1000ByOrderByCreatedAtDesc()).thenReturn(List.of(log));

        PageResult<BetaVerifyLogDto> masked = service.listLogs(1, 20, null, null, true);
        assertEquals("123****89", masked.getList().get(0).getQq());

        PageResult<BetaVerifyLogDto> plain = service.listLogs(1, 20, null, null, false);
        assertEquals("123456789", plain.getList().get(0).getQq());

        // 按原始 QQ 过滤（脱敏前）仍然命中
        assertEquals(1, service.listLogs(1, 20, "123456789", "reject", true).getTotal());
        assertEquals(1, service.listLogs(1, 20, "steve", "reject", true).getTotal(), "应能按游戏ID 过滤");
        assertEquals(0, service.listLogs(1, 20, null, "pass", true).getTotal());
    }

    // ---------------- 辅助 ----------------

    /** 简化 verify 调用（固定 IP / UA）。注意：方法名不能叫 verify，否则会整体遮蔽静态导入的 Mockito.verify。 */
    private BetaWhitelistService.VerifyOutcome verifyAs(String qq, String name) {
        return service.verify(qq, name, "1.2.3.4", "UA");
    }

    private void stubQq(String qq, BetaWhitelist... entries) {
        when(whitelistRepo.findAllByQqOrderByCreatedAtAsc(qq)).thenReturn(List.of(entries));
    }

    private BetaWhitelistDto createWithExpire(String expireAt) {
        BetaWhitelistRequest request = new BetaWhitelistRequest();
        request.setQq("123456789");
        request.setMcId("Steve");
        request.setExpireAt(expireAt);
        return service.create(request, "admin");
    }

    private BetaWhitelistRequest item(String qq) {
        BetaWhitelistRequest request = new BetaWhitelistRequest();
        request.setQq(qq);
        return request;
    }

    private BetaWhitelist entry(String qq, String mcId, int status, LocalDateTime expireAt) {
        BetaWhitelist entry = new BetaWhitelist();
        entry.setQq(qq);
        entry.setMcId(mcId);
        entry.setStatus(status);
        entry.setExpireAt(expireAt);
        return entry;
    }
}
