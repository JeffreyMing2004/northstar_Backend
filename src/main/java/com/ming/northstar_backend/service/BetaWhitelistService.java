package com.ming.northstar_backend.service;

import com.ming.northstar_backend.dto.*;
import com.ming.northstar_backend.entity.BetaVerifyLog;
import com.ming.northstar_backend.entity.BetaWhitelist;
import com.ming.northstar_backend.repository.BetaVerifyLogRepository;
import com.ming.northstar_backend.repository.BetaWhitelistRepository;
import com.ming.northstar_backend.support.McIdFormat;
import com.ming.northstar_backend.support.QqFormat;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.stream.Collectors;

/**
 * 北极战区内测白名单与客户端资格校验。
 *
 * <p>接口 A（{@code GET /api/beta/verify}）与接口 B（{@code /api/admin/beta/whitelist}）
 * 的业务逻辑都集中在这里。</p>
 *
 * <p><b>身份判定只用「QQ + 游戏ID」</b>。北极战区是离线模式服务器，玩家 UUID 由启动器
 * 自行生成、每次启动可能变化，服务端无法据此识别身份，因此不参与校验。判定顺序：</p>
 * <ol>
 *   <li>QQ 必须在白名单中，且条目处于「启用且未过期」状态</li>
 *   <li>若条目绑定了游戏 ID（{@code mcId}），上报的游戏 ID 必须与之一致（忽略大小写）</li>
 *   <li>若条目未绑定游戏 ID，则在 {@code northstar.verify.require-mc-id=false}（默认）时
 *       按任意游戏 ID 放行；置为 {@code true} 则一律判为未通过</li>
 * </ol>
 *
 * <p>白名单条目的来源分两类：运营在后台手工录入 / 导入的（{@code source=manual}），
 * 以及账号审批通过后由 {@link #syncApprovedPlayer} 自动同步的（{@code source=account}）。
 * 自动同步只重建自己创建的条目，不会覆盖人工记录。</p>
 */
@Service
public class BetaWhitelistService {

    /** 后台录入「到期时间」时允许的写法。 */
    private static final List<DateTimeFormatter> EXPIRE_FORMATS = List.of(
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm")
    );

    /** 游戏 ID 格式判定统一走 {@link McIdFormat}，不再本地维护正则。 */

    private final BetaWhitelistRepository whitelistRepo;
    private final BetaVerifyLogRepository logRepo;
    private final QqFormat qqFormat;
    private final int rateLimitPerMinute;
    /** 是否强制要求白名单条目已绑定游戏 ID。 */
    private final boolean requireGameId;
    private final Map<String, ConcurrentLinkedDeque<Long>> rateHits = new ConcurrentHashMap<>();

    public BetaWhitelistService(BetaWhitelistRepository whitelistRepo,
                                BetaVerifyLogRepository logRepo,
                                QqFormat qqFormat,
                                @Value("${northstar.verify.rate-limit-per-minute:60}") int rateLimitPerMinute,
                                @Value("${northstar.verify.require-mc-id:false}") boolean requireGameId) {
        this.whitelistRepo = whitelistRepo;
        this.logRepo = logRepo;
        this.qqFormat = qqFormat;
        this.rateLimitPerMinute = rateLimitPerMinute;
        this.requireGameId = requireGameId;
    }

    // ------------------------------------------------------------------
    // 接口 A：内测资格校验
    // ------------------------------------------------------------------

    /** 校验结果 + 对应 HTTP 状态码。 */
    public record VerifyOutcome(int httpStatus, BetaVerifyResponse body) {
    }

    /**
     * 校验一个「QQ + 游戏ID」是否具备内测资格。
     *
     * <p>本方法<b>不抛异常</b>：所有异常都会转成 HTTP 5xx 的「服务不可用」结果，
     * 这样客户端不会误判为「明确未通过」而崩溃，同时日志一定写得进去。</p>
     */
    public VerifyOutcome verify(String rawQq, String playerName, String ip, String userAgent) {
        long startedAt = System.currentTimeMillis();
        String qq = rawQq == null ? "" : rawQq.trim();
        String name = truncate(playerName, 64);

        if (!qqFormat.isValid(qq)) {
            BetaVerifyLog log = newLog(qq, name, ip, userAgent);
            saveLog(log, null, BetaVerifyLog.RESULT_ERROR, 400, startedAt, "QQ 号格式无效");
            return new VerifyOutcome(400, BetaVerifyResponse.badRequest("QQ 号格式无效"));
        }

        // 离线模式下游戏ID 是唯一身份依据之一，缺了就无法判断，只能当作可重试的参数错误
        if (name == null) {
            BetaVerifyLog log = newLog(qq, null, ip, userAgent);
            saveLog(log, null, BetaVerifyLog.RESULT_ERROR, 400, startedAt, "缺少游戏ID");
            return new VerifyOutcome(400, BetaVerifyResponse.badRequest("缺少游戏ID（name 参数）"));
        }

        if (!allowRequest(ip)) {
            BetaVerifyLog log = newLog(qq, name, ip, userAgent);
            saveLog(log, null, BetaVerifyLog.RESULT_ERROR, 429, startedAt, "请求过于频繁");
            return new VerifyOutcome(429, BetaVerifyResponse.tooManyRequests("请求过于频繁，请稍后重试"));
        }

        try {
            LocalDateTime now = LocalDateTime.now();
            List<BetaWhitelist> rows = whitelistRepo.findAllByQqOrderByCreatedAtAsc(qq);
            BetaVerifyLog log = newLog(qq, name, ip, userAgent);

            if (rows.isEmpty()) {
                saveLog(log, null, BetaVerifyLog.RESULT_REJECT, 200, startedAt, "QQ 不在白名单");
                return new VerifyOutcome(200, BetaVerifyResponse.reject("该 QQ 未获得内测资格"));
            }

            List<BetaWhitelist> usable = rows.stream().filter(entry -> entry.isUsable(now)).toList();
            if (usable.isEmpty()) {
                boolean disabled = rows.stream()
                        .anyMatch(entry -> !Objects.equals(entry.getStatus(), BetaWhitelist.STATUS_ENABLED));
                String reason = disabled ? "资格已被禁用" : "资格已过期";
                saveLog(log, null, BetaVerifyLog.RESULT_REJECT, 200, startedAt, reason);
                return new VerifyOutcome(200, BetaVerifyResponse.reject("该 QQ 的" + reason));
            }

            // 1) 优先精确命中绑定的游戏 ID
            for (BetaWhitelist entry : usable) {
                if (entry.matchesGameId(name)) {
                    saveLog(log, BetaVerifyLog.MATCH_BOUND, BetaVerifyLog.RESULT_PASS, 200, startedAt, null);
                    return new VerifyOutcome(200, BetaVerifyResponse.pass(
                            BetaVerifyData.from(entry, name, BetaVerifyLog.MATCH_BOUND)));
                }
            }

            // 2) 未绑定游戏 ID 的条目：默认按「任意游戏ID」放行
            if (!requireGameId) {
                for (BetaWhitelist entry : usable) {
                    if (entry.getBoundGameId() == null) {
                        saveLog(log, BetaVerifyLog.MATCH_UNBOUND, BetaVerifyLog.RESULT_PASS, 200, startedAt,
                                "白名单未绑定游戏ID");
                        return new VerifyOutcome(200, BetaVerifyResponse.pass(
                                BetaVerifyData.from(entry, name, BetaVerifyLog.MATCH_UNBOUND)));
                    }
                }
            }

            String bound = usable.stream()
                    .map(BetaWhitelist::getBoundGameId)
                    .filter(Objects::nonNull)
                    .distinct()
                    .collect(Collectors.joining("/"));
            String reason;
            String message;
            if (bound.isEmpty()) {
                reason = "该 QQ 未绑定游戏ID";
                message = "该 QQ 未登记游戏ID，请联系管理员补充绑定";
            } else {
                reason = "游戏ID 与绑定不一致（已绑定 " + bound + "）";
                message = "游戏ID「" + name + "」与白名单绑定的不一致（已绑定：" + bound + "）";
            }
            saveLog(log, null, BetaVerifyLog.RESULT_REJECT, 200, startedAt, reason);
            return new VerifyOutcome(200, BetaVerifyResponse.reject(message));
        } catch (Exception e) {
            // 任何内部异常都必须是「服务不可用」：返回 5xx，客户端按可重试处理，不会崩溃。
            String msg = "校验服务暂时不可用：" + e.getClass().getSimpleName();
            saveLog(newLog(qq, name, ip, userAgent), null, BetaVerifyLog.RESULT_ERROR, 500, startedAt, msg);
            return new VerifyOutcome(500, BetaVerifyResponse.serverError(msg));
        }
    }

    /** 内存滑动窗口限流，按来源 IP 计。{@code rateLimitPerMinute <= 0} 表示不限流。 */
    private boolean allowRequest(String ip) {
        if (rateLimitPerMinute <= 0) {
            return true;
        }
        String key = ip == null || ip.isBlank() ? "unknown" : ip;
        long now = System.currentTimeMillis();
        long windowStart = now - 60_000L;

        ConcurrentLinkedDeque<Long> hits = rateHits.computeIfAbsent(key, k -> new ConcurrentLinkedDeque<>());
        synchronized (hits) {
            while (!hits.isEmpty() && hits.peekFirst() < windowStart) {
                hits.pollFirst();
            }
            if (hits.size() >= rateLimitPerMinute) {
                return false;
            }
            hits.addLast(now);
        }
        // 防止 IP 数量无限增长
        if (rateHits.size() > 5000) {
            rateHits.entrySet().removeIf(entry -> entry.getValue().isEmpty());
        }
        return true;
    }

    private BetaVerifyLog newLog(String qq, String name, String ip, String userAgent) {
        BetaVerifyLog log = new BetaVerifyLog();
        log.setQq(truncate(qq, 16));
        log.setPlayerName(name);
        log.setIp(truncate(ip, 64));
        log.setUserAgent(truncate(userAgent, 255));
        log.setCreatedAt(LocalDateTime.now());
        return log;
    }

    private void saveLog(BetaVerifyLog log, String matchedBy, String result,
                         int httpStatus, long startedAt, String reason) {
        try {
            log.setMatchedBy(matchedBy);
            log.setResult(result);
            log.setHttpStatus(httpStatus);
            log.setCostMs(System.currentTimeMillis() - startedAt);
            log.setReason(truncate(reason, 128));
            logRepo.save(log);
        } catch (Exception ignored) {
            // 日志写失败绝不能影响校验结果
        }
    }

    // ------------------------------------------------------------------
    // 接口 B：白名单管理
    // ------------------------------------------------------------------

    public PageResult<BetaWhitelistDto> list(int page, int size, String keyword, Integer status) {
        String value = keyword == null ? "" : keyword.trim().toLowerCase(Locale.ROOT);
        LocalDateTime now = LocalDateTime.now();

        List<BetaWhitelistDto> all = whitelistRepo.findAllByOrderByCreatedAtDesc().stream()
                .filter(entry -> status == null || status.equals(entry.getStatus()))
                .filter(entry -> value.isBlank()
                        || contains(entry.getQq(), value)
                        || contains(entry.getMcId(), value)
                        || contains(entry.getNickname(), value)
                        || contains(entry.getRemark(), value))
                .map(entry -> BetaWhitelistDto.from(entry, entry.isUsable(now)))
                .collect(Collectors.toList());

        return slice(all, page, size);
    }

    @Transactional
    public BetaWhitelistDto create(BetaWhitelistRequest request, String operator) {
        String qq = requireValidQq(request.getQq());
        BetaWhitelist entry = new BetaWhitelist();
        entry.setQq(qq);
        applyRequest(entry, request, true);
        requireNoDuplicate(qq, entry.getBoundGameId(), null);
        entry.setSource(BetaWhitelist.SOURCE_MANUAL);
        entry.setCreatedBy(truncate(operator, 64));
        entry.setCreatedAt(LocalDateTime.now());
        entry.setUpdatedAt(LocalDateTime.now());
        return BetaWhitelistDto.from(whitelistRepo.save(entry), entry.isUsable(LocalDateTime.now()));
    }

    @Transactional
    public BetaWhitelistDto update(Long id, BetaWhitelistRequest request) {
        BetaWhitelist entry = whitelistRepo.findById(id)
                .orElseThrow(() -> new RuntimeException("白名单条目不存在"));

        if (request.getQq() != null && !request.getQq().isBlank()) {
            entry.setQq(requireValidQq(request.getQq()));
        }
        applyRequest(entry, request, false);
        requireNoDuplicate(entry.getQq(), entry.getBoundGameId(), id);
        entry.setUpdatedAt(LocalDateTime.now());
        return BetaWhitelistDto.from(whitelistRepo.save(entry), entry.isUsable(LocalDateTime.now()));
    }

    @Transactional
    public void delete(Long id) {
        if (!whitelistRepo.existsById(id)) {
            throw new RuntimeException("白名单条目不存在");
        }
        whitelistRepo.deleteById(id);
    }

    /** 同一 QQ 下不允许出现重复的游戏 ID 绑定（两个「未绑定」也算重复）。 */
    private void requireNoDuplicate(String qq, String gameId, Long excludeId) {
        boolean duplicated = whitelistRepo.findAllByQqOrderByCreatedAtAsc(qq).stream()
                .filter(other -> excludeId == null || !excludeId.equals(other.getId()))
                .anyMatch(other -> sameGameId(other.getBoundGameId(), gameId));
        if (duplicated) {
            throw new RuntimeException(gameId == null
                    ? "该 QQ 已有一条「不限定游戏ID」的白名单记录"
                    : "该 QQ 已绑定游戏ID「" + gameId + "」");
        }
    }

    @Transactional
    public BetaWhitelistImportResult importBatch(BetaWhitelistImportRequest request, String operator) {
        if (Boolean.TRUE.equals(request.getReplace())) {
            whitelistRepo.deleteAll();
        }
        BetaWhitelistImportResult result = new BetaWhitelistImportResult();
        int line = 0;
        for (BetaWhitelistRequest item : request.getItems()) {
            line++;
            String qq = item.getQq() == null ? "" : item.getQq().trim();
            if (qq.isEmpty()) {
                result.setSkipped(result.getSkipped() + 1);
                continue;
            }
            if (!qqFormat.isValid(qq)) {
                result.addError(line, qq, "QQ 号格式无效");
                continue;
            }
            upsert(qq, item, operator, result, line);
        }
        return result;
    }

    /**
     * 按行导入纯文本名单，每行 {@code qq,gameId,nickname,remark,expireAt}。
     *
     * <p>第一行若以 {@code qq} 开头会被当作表头跳过；分隔符支持逗号与制表符；
     * {@code #} 开头的行视为注释。{@code gameId} 留空表示不限定游戏 ID。</p>
     */
    @Transactional
    public BetaWhitelistImportResult importCsv(String text, boolean replace, String operator) {
        if (replace) {
            whitelistRepo.deleteAll();
        }
        BetaWhitelistImportResult result = new BetaWhitelistImportResult();
        if (text == null || text.isBlank()) {
            return result;
        }

        String[] lines = text.replace("\r\n", "\n").replace('\r', '\n').split("\n");
        int line = 0;
        for (String raw : lines) {
            line++;
            String row = raw.trim();
            if (row.isEmpty() || row.startsWith("#")) {
                result.setSkipped(result.getSkipped() + 1);
                continue;
            }
            String[] cells = row.split("[,\t]", -1);
            String qq = cells[0].trim().replace("\"", "");
            if (line == 1 && qq.equalsIgnoreCase("qq")) {
                continue;
            }
            if (qq.isEmpty()) {
                result.setSkipped(result.getSkipped() + 1);
                continue;
            }
            if (!qqFormat.isValid(qq)) {
                result.addError(line, qq, "QQ 号格式无效");
                continue;
            }

            BetaWhitelistRequest item = new BetaWhitelistRequest();
            item.setQq(qq);
            if (cells.length > 1) item.setMcId(cleanCell(cells[1]));
            if (cells.length > 2) item.setNickname(cleanCell(cells[2]));
            if (cells.length > 3) item.setRemark(cleanCell(cells[3]));
            if (cells.length > 4) item.setExpireAt(cleanCell(cells[4]));
            upsert(qq, item, operator, result, line);
        }
        return result;
    }

    /**
     * 按「QQ + 绑定的游戏ID」判断是新增还是覆盖。
     *
     * <p>单行出错（例如游戏ID 格式非法）只记入失败明细，不影响其它行。</p>
     */
    private void upsert(String qq, BetaWhitelistRequest item, String operator,
                        BetaWhitelistImportResult result, int line) {
        try {
            BetaWhitelist probe = new BetaWhitelist();
            probe.setQq(qq);
            applyRequest(probe, item, true);

            String gameId = probe.getBoundGameId();
            Optional<BetaWhitelist> existing = whitelistRepo.findAllByQqOrderByCreatedAtAsc(qq).stream()
                    .filter(entry -> sameGameId(entry.getBoundGameId(), gameId))
                    .findFirst();

            if (existing.isPresent()) {
                BetaWhitelist entry = existing.get();
                applyRequest(entry, item, true);
                entry.setSource(BetaWhitelist.SOURCE_MANUAL);
                entry.setUpdatedAt(LocalDateTime.now());
                whitelistRepo.save(entry);
                result.setUpdated(result.getUpdated() + 1);
                return;
            }

            probe.setSource(BetaWhitelist.SOURCE_MANUAL);
            probe.setCreatedBy(truncate(operator, 64));
            probe.setCreatedAt(LocalDateTime.now());
            probe.setUpdatedAt(LocalDateTime.now());
            whitelistRepo.save(probe);
            result.setCreated(result.getCreated() + 1);
        } catch (RuntimeException e) {
            result.addError(line, qq, e.getMessage());
        }
    }

    /** 导出为 CSV 文本，含表头。 */
    public String exportCsv() {
        LocalDateTime now = LocalDateTime.now();
        StringBuilder sb = new StringBuilder(
                "qq,gameId,nickname,gameIdBound,status,active,remark,expireAt,createdAt,createdBy\n");
        for (BetaWhitelist entry : whitelistRepo.findAllByOrderByCreatedAtDesc()) {
            sb.append(csv(entry.getQq())).append(',')
                    .append(csv(entry.getBoundGameId())).append(',')
                    .append(csv(entry.getNickname())).append(',')
                    .append(entry.getBoundGameId() == null ? 0 : 1).append(',')
                    .append(entry.getStatus() == null ? 1 : entry.getStatus()).append(',')
                    .append(entry.isUsable(now) ? 1 : 0).append(',')
                    .append(csv(entry.getRemark())).append(',')
                    .append(entry.getExpireAt() == null ? "" : entry.getExpireAt().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")))
                    .append(',')
                    .append(entry.getCreatedAt() == null ? "" : entry.getCreatedAt().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")))
                    .append(',')
                    .append(csv(entry.getCreatedBy()))
                    .append('\n');
        }
        return sb.toString();
    }

    public PageResult<BetaVerifyLogDto> listLogs(int page, int size, String keyword,
                                                String result, boolean maskQq) {
        String value = keyword == null ? "" : keyword.trim();
        String wanted = result == null ? "" : result.trim().toLowerCase(Locale.ROOT);

        List<BetaVerifyLogDto> all = logRepo.findTop1000ByOrderByCreatedAtDesc().stream()
                .filter(log -> wanted.isBlank() || wanted.equalsIgnoreCase(log.getResult()))
                .filter(log -> value.isBlank()
                        || contains(log.getQq(), value)
                        || contains(log.getPlayerName(), value)
                        || contains(log.getIp(), value))
                .map(log -> BetaVerifyLogDto.from(log, maskQq))
                .collect(Collectors.toList());

        return slice(all, page, size);
    }

    // ------------------------------------------------------------------
    // 账号审批 -> 白名单同步
    // ------------------------------------------------------------------

    /** 同步结果类别。 */
    public enum SyncOutcome {
        /** 新建了白名单条目。 */
        CREATED,
        /** 已有完全相同的条目，未做改动（保留运营对状态 / 到期时间的修改）。 */
        UNCHANGED,
        /** 删掉旧的系统条目，按最新「QQ + 游戏ID」重建。 */
        REFRESHED,
        /** 账号缺少 QQ 号，无法同步。 */
        MISSING_QQ,
        /** QQ 号或游戏ID 格式非法，无法同步。 */
        INVALID_FORMAT
    }

    /** 同步结果；失败时 {@code entry} 为 {@code null}。 */
    public record SyncResult(SyncOutcome outcome, BetaWhitelist entry) {
        public boolean succeeded() {
            return outcome == SyncOutcome.CREATED
                    || outcome == SyncOutcome.UNCHANGED
                    || outcome == SyncOutcome.REFRESHED;
        }
    }

    /**
     * 把「已获批的内测玩家」同步成白名单条目。
     *
     * <p>注册平台在注册时就要求玩家提交 QQ 与离线服游戏 ID。审批通过后必须把它们落成
     * 白名单，否则 {@link #verify} 会一律判「该 QQ 未获得内测资格」，玩家一进游戏就崩溃。
     * 有了本方法，运营不必再手工维护白名单。</p>
     *
     * <p>幂等规则（只动自己创建的条目，绝不覆盖人工录入的记录）：</p>
     * <ol>
     *   <li>已存在完全相同的 {@code (qq, 游戏ID)} 条目 → 原样返回，不动启用状态与到期时间</li>
     *   <li>否则删除该 QQ 下所有 {@code source=account} 的旧条目，按当前值重建一条
     *       「启用、永不过期」的条目，避免玩家改了游戏ID 或 QQ 后留下旧的通行证</li>
     * </ol>
     *
     * <p>本方法<b>不抛异常</b>：失败原因放在返回值里，由调用方决定是阻断审批还是仅告警。</p>
     */
    @Transactional
    public SyncResult syncApprovedPlayer(String rawQq, String rawMcId, String nickname,
                                         String remark, String operator) {
        String qq = qqFormat.normalize(rawQq);
        if (qq.isEmpty()) {
            return new SyncResult(SyncOutcome.MISSING_QQ, null);
        }
        if (!qqFormat.isValid(qq)) {
            return new SyncResult(SyncOutcome.INVALID_FORMAT, null);
        }
        String gameId = blankToNull(rawMcId);
        if (gameId != null && !McIdFormat.isValid(gameId)) {
            return new SyncResult(SyncOutcome.INVALID_FORMAT, null);
        }

        LocalDateTime now = LocalDateTime.now();
        List<BetaWhitelist> rows = whitelistRepo.findAllByQqOrderByCreatedAtAsc(qq);

        // 1) 已经存在完全相同的授权 -> 保持原样
        for (BetaWhitelist row : rows) {
            if (sameGameId(row.getBoundGameId(), gameId)) {
                return new SyncResult(SyncOutcome.UNCHANGED, row);
            }
        }

        // 2) 清掉旧的系统条目后重建
        List<BetaWhitelist> owned = rows.stream().filter(BetaWhitelist::isSystemManaged).toList();
        boolean refreshed = !owned.isEmpty();
        if (refreshed) {
            whitelistRepo.deleteAll(owned);
        }

        BetaWhitelist entry = new BetaWhitelist();
        entry.setQq(qq);
        entry.setMcId(gameId);
        entry.setNickname(truncate(blankToNull(nickname), 64));
        entry.setRemark(truncate(blankToNull(remark), 255));
        entry.setStatus(BetaWhitelist.STATUS_ENABLED);
        entry.setSource(BetaWhitelist.SOURCE_ACCOUNT);
        entry.setCreatedBy(truncate(operator, 64));
        entry.setCreatedAt(now);
        entry.setUpdatedAt(now);
        return new SyncResult(refreshed ? SyncOutcome.REFRESHED : SyncOutcome.CREATED,
                whitelistRepo.save(entry));
    }

    // ------------------------------------------------------------------
    // 账号 -> 白名单：查看与撤销
    // ------------------------------------------------------------------

    /** 客户端白名单对某个账号的可用性（供账号设置页展示）。 */
    public enum QualificationStatus {
        /** 条目存在，且处于启用、未过期状态。 */
        ACTIVE,
        /** 条目存在但被运营禁用。 */
        DISABLED,
        /** 条目存在但已过期。 */
        EXPIRED,
        /** 该账号没有任何白名单条目（还没获批，或资格刚被取消）。 */
        MISSING
    }

    /**
     * 白名单视角的资格描述。
     *
     * @param status 可用性
     * @param entry  命中的条目；{@code MISSING} 时为 {@code null}
     */
    public record Qualification(QualificationStatus status, BetaWhitelist entry) {
        public boolean usable() {
            return status == QualificationStatus.ACTIVE;
        }
    }

    /**
     * 查询某个「QQ + 游戏ID」当前的白名单状态，供玩家在账号设置页自查。
     *
     * <p>与 {@link #verify} 的区别：不做限流、不写日志，只回答「白名单里现在长什么样」。
     * 挑选口径与 {@code verify} 保持一致：同一 QQ 下优先选绑定游戏 ID 完全相同的那条，
     * 其次选「不限定游戏 ID」的那条，最后才退到任意一条（便于提示「已停用 / 已过期」）。</p>
     */
    @Transactional(readOnly = true)
    public Qualification describeQualification(String rawQq, String rawMcId) {
        String qq = qqFormat.normalize(rawQq);
        if (qq.isEmpty()) {
            return new Qualification(QualificationStatus.MISSING, null);
        }

        List<BetaWhitelist> rows = whitelistRepo.findAllByQqOrderByCreatedAtAsc(qq);
        if (rows.isEmpty()) {
            return new Qualification(QualificationStatus.MISSING, null);
        }

        BetaWhitelist matched = null;    // 绑定游戏 ID 完全一致的
        BetaWhitelist unbounded = null;  // 不限定游戏 ID 的
        for (BetaWhitelist row : rows) {
            if (row.getBoundGameId() == null) {
                if (unbounded == null) {
                    unbounded = row;
                }
            } else if (matched == null && row.matchesGameId(rawMcId)) {
                matched = row;
            }
        }
        BetaWhitelist chosen = matched != null ? matched : unbounded;
        if (chosen == null) {
            chosen = rows.get(0);
        }

        LocalDateTime now = LocalDateTime.now();
        if (chosen.isUsable(now)) {
            return new Qualification(QualificationStatus.ACTIVE, chosen);
        }
        if (chosen.getExpireAt() != null && !chosen.getExpireAt().isAfter(now)) {
            return new Qualification(QualificationStatus.EXPIRED, chosen);
        }
        return new Qualification(QualificationStatus.DISABLED, chosen);
    }

    /**
     * 取消内测资格时删除「系统同步」的白名单条目。
     *
     * <p>资格与白名单必须同进退：后台把某个玩家的内测资格取消掉，如果白名单还留着，
     * 他照样能进游戏——「取消」就成了一句空话。本方法与 {@link #syncApprovedPlayer}
     * 成对使用，分别负责两个方向。</p>
     *
     * <p><b>只删 {@code source=account} 的条目</b>：运营在后台手工录入 / 导入的记录
     * （例如给主播、赞助者单独开的白名单）不属于任何账号审批，撤资格时绝不能被连带清掉。</p>
     *
     * <p>之所以要传多个身份值：管理员可能在同一笔操作里既改了 QQ / 游戏 ID 又取消了资格，
     * 只按「当前值」删就会留下用旧 QQ 建的孤儿条目，那条记录会一直在白名单里放行。</p>
     *
     * @param rawQqs   相关的 QQ（改动前后都传进来）
     * @param rawMcIds 相关的游戏 ID（改动前后都传进来）
     * @return 实际删除的条目数；0 表示本来就没有系统同步的条目（幂等）
     */
    @Transactional
    public int revokeAccountEntries(Collection<String> rawQqs, Collection<String> rawMcIds) {
        Set<String> qqs = normalizeKeys(rawQqs, true);
        Set<String> gameIds = normalizeKeys(rawMcIds, false);
        if (qqs.isEmpty() && gameIds.isEmpty()) {
            return 0;
        }

        Map<Long, BetaWhitelist> victims = new LinkedHashMap<>();
        for (String qq : qqs) {
            for (BetaWhitelist row : whitelistRepo.findAllByQqOrderByCreatedAtAsc(qq)) {
                if (row.isSystemManaged() && row.getId() != null) {
                    victims.put(row.getId(), row);
                }
            }
        }
        if (!gameIds.isEmpty()) {
            for (BetaWhitelist row : listAccountManagedEntries()) {
                String bound = row.getBoundGameId();
                if (row.getId() != null && bound != null && gameIds.contains(bound.toLowerCase(Locale.ROOT))) {
                    victims.put(row.getId(), row);
                }
            }
        }

        if (victims.isEmpty()) {
            return 0;
        }
        whitelistRepo.deleteAll(victims.values());
        return victims.size();
    }

    /**
     * 删除指定的系统同步条目（对账批量清理用）。人工条目一律跳过。
     *
     * @return 实际删除的条数
     */
    @Transactional
    public int deleteAccountManagedEntries(Collection<BetaWhitelist> entries) {
        if (entries == null || entries.isEmpty()) {
            return 0;
        }
        List<BetaWhitelist> victims = entries.stream()
                .filter(entry -> entry != null && entry.isSystemManaged() && entry.getId() != null)
                .collect(Collectors.toList());
        if (victims.isEmpty()) {
            return 0;
        }
        whitelistRepo.deleteAll(victims);
        return victims.size();
    }

    /** 全部由账号审批自动同步而来的条目（用于对账 / 修复历史数据）。 */
    @Transactional(readOnly = true)
    public List<BetaWhitelist> listAccountManagedEntries() {
        return whitelistRepo.findAllByOrderByCreatedAtDesc().stream()
                .filter(BetaWhitelist::isSystemManaged)
                .collect(Collectors.toList());
    }

    /** 去除空白与空值；{@code lowerCase} 用于游戏 ID 的忽略大小写比较。 */
    private Set<String> normalizeKeys(Collection<String> raw, boolean lowerCase) {
        if (raw == null) {
            return Set.of();
        }
        return raw.stream()
                .map(McIdFormat::normalize)
                .filter(value -> !value.isEmpty())
                .map(value -> lowerCase ? value.toLowerCase(Locale.ROOT) : value)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    // ------------------------------------------------------------------
    // 内部工具
    // ------------------------------------------------------------------

    private void applyRequest(BetaWhitelist entry, BetaWhitelistRequest request, boolean overwrite) {
        if (request.getNickname() != null) {
            entry.setNickname(truncate(blankToNull(request.getNickname()), 64));
        }
        if (request.getMcId() != null) {
            String gameId = blankToNull(request.getMcId());
            if (gameId != null && !McIdFormat.isValid(gameId)) {
                throw new RuntimeException("游戏ID 格式无效（3-16 位字母、数字或下划线）");
            }
            entry.setMcId(gameId);
        }
        if (request.getRemark() != null) {
            entry.setRemark(truncate(blankToNull(request.getRemark()), 255));
        }
        if (request.getStatus() != null) {
            if (request.getStatus() != BetaWhitelist.STATUS_ENABLED
                    && request.getStatus() != BetaWhitelist.STATUS_DISABLED) {
                throw new RuntimeException("状态只能为 1（启用）或 0（禁用）");
            }
            entry.setStatus(request.getStatus());
        } else if (overwrite && entry.getStatus() == null) {
            entry.setStatus(BetaWhitelist.STATUS_ENABLED);
        }
        if (request.getExpireAt() != null) {
            entry.setExpireAt(parseExpireAt(request.getExpireAt()));
        }
    }

    private LocalDateTime parseExpireAt(String raw) {
        String value = raw == null ? "" : raw.trim();
        if (value.isEmpty()) {
            return null;
        }
        if (value.matches("^\\d{10,13}$")) {
            long millis = Long.parseLong(value);
            if (value.length() == 10) {
                millis *= 1000L;
            }
            return LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), ZoneId.systemDefault());
        }
        if (value.length() == 10) {
            return LocalDate.parse(value, DateTimeFormatter.ofPattern("yyyy-MM-dd")).atTime(23, 59, 59);
        }
        for (DateTimeFormatter formatter : EXPIRE_FORMATS) {
            try {
                return LocalDateTime.parse(value, formatter);
            } catch (DateTimeParseException ignored) {
                // 继续尝试下一个格式
            }
        }
        throw new RuntimeException("到期时间格式无效，应为 yyyy-MM-dd 或 yyyy-MM-dd HH:mm");
    }

    private String requireValidQq(String raw) {
        String qq = raw == null ? "" : raw.trim();
        if (!qqFormat.isValid(qq)) {
            throw new RuntimeException("QQ 号格式无效");
        }
        return qq;
    }

    private <T> PageResult<T> slice(List<T> all, int page, int size) {
        int safeSize = Math.min(Math.max(size, 1), 500);
        int safePage = Math.max(page, 1);
        int from = Math.min((safePage - 1) * safeSize, all.size());
        int to = Math.min(from + safeSize, all.size());
        return new PageResult<>(all.size(), safePage, safeSize, new ArrayList<>(all.subList(from, to)));
    }

    /** 游戏 ID 比较：忽略大小写，null 与 null 视为相同。 */
    private static boolean sameGameId(String a, String b) {
        if (a == null || b == null) {
            return a == null && b == null;
        }
        return a.equalsIgnoreCase(b);
    }

    private static boolean contains(String value, String keyword) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(keyword.toLowerCase(Locale.ROOT));
    }

    private static String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String cleanCell(String cell) {
        return cell == null ? null : cell.trim().replace("\"", "");
    }

    private static String csv(String value) {
        if (value == null) {
            return "";
        }
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return '"' + value.replace("\"", "\"\"") + '"';
        }
        return value;
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.length() <= max) {
            return trimmed.isEmpty() ? null : trimmed;
        }
        return trimmed.substring(0, max);
    }
}
