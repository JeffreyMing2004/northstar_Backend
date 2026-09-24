package com.ming.northstar_backend.service;

import com.ming.northstar_backend.dto.*;
import com.ming.northstar_backend.entity.BetaVerifyLog;
import com.ming.northstar_backend.entity.BetaWhitelist;
import com.ming.northstar_backend.repository.BetaVerifyLogRepository;
import com.ming.northstar_backend.repository.BetaWhitelistRepository;
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
import java.util.regex.Pattern;
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

    /** 游戏 ID 的合法格式，与客户端登录名一致。 */
    private static final Pattern GAME_ID_PATTERN = Pattern.compile("^[a-zA-Z0-9_]{3,16}$");

    private final BetaWhitelistRepository whitelistRepo;
    private final BetaVerifyLogRepository logRepo;
    private final Pattern qqPattern;
    private final int rateLimitPerMinute;
    /** 是否强制要求白名单条目已绑定游戏 ID。 */
    private final boolean requireGameId;
    private final Map<String, ConcurrentLinkedDeque<Long>> rateHits = new ConcurrentHashMap<>();

    public BetaWhitelistService(BetaWhitelistRepository whitelistRepo,
                                BetaVerifyLogRepository logRepo,
                                @Value("${northstar.verify.qq-pattern:^[1-9]\\d{4,10}$}") String qqPattern,
                                @Value("${northstar.verify.rate-limit-per-minute:60}") int rateLimitPerMinute,
                                @Value("${northstar.verify.require-mc-id:false}") boolean requireGameId) {
        this.whitelistRepo = whitelistRepo;
        this.logRepo = logRepo;
        this.qqPattern = Pattern.compile(qqPattern);
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

        if (!qqPattern.matcher(qq).matches()) {
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
            if (!qqPattern.matcher(qq).matches()) {
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
            if (!qqPattern.matcher(qq).matches()) {
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
                entry.setUpdatedAt(LocalDateTime.now());
                whitelistRepo.save(entry);
                result.setUpdated(result.getUpdated() + 1);
                return;
            }

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
    // 内部工具
    // ------------------------------------------------------------------

    private void applyRequest(BetaWhitelist entry, BetaWhitelistRequest request, boolean overwrite) {
        if (request.getNickname() != null) {
            entry.setNickname(truncate(blankToNull(request.getNickname()), 64));
        }
        if (request.getMcId() != null) {
            String gameId = blankToNull(request.getMcId());
            if (gameId != null && !GAME_ID_PATTERN.matcher(gameId).matches()) {
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
        if (!qqPattern.matcher(qq).matches()) {
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
