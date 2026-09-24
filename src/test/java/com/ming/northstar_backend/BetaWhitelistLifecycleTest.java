package com.ming.northstar_backend;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ming.northstar_backend.dto.AdminUserUpdateRequest;
import com.ming.northstar_backend.dto.BetaWhitelistRequest;
import com.ming.northstar_backend.dto.WhitelistSyncResult;
import com.ming.northstar_backend.entity.BetaWhitelist;
import com.ming.northstar_backend.entity.User;
import com.ming.northstar_backend.repository.BetaWhitelistRepository;
import com.ming.northstar_backend.repository.UserRepository;
import com.ming.northstar_backend.service.AdminService;
import com.ming.northstar_backend.service.BetaWhitelistService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 「内测资格与客户端白名单同进同退」的真实 HTTP 回归测试。
 *
 * <p>要守住的两条不变式：</p>
 * <ol>
 *   <li><b>拿到内测资格 ⇒ 自动拥有白名单</b>。玩家在后台被批准后不必再找运营手工加白名单，
 *       否则就会出现「平台显示已通过、进游戏却被判未通过而崩溃」。</li>
 *   <li><b>取消内测资格 ⇒ 白名单随之消失</b>。否则「取消」只是一句空话，玩家照样能进游戏。</li>
 * </ol>
 *
 * <p>同时确认第三条边界：运营手工录入 / 导入的白名单（例如给主播单独开的）
 * 不属于任何账号审批，两个方向的操作都不能碰到它。</p>
 *
 * <p>QQ 只用与其它测试类不冲突的号段，避免共享的 H2 上下文互相干扰。</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class BetaWhitelistLifecycleTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final HttpClient CLIENT = HttpClient.newHttpClient();

    @Value("${local.server.port}")
    private int port;

    @Autowired
    private AdminService adminService;

    @Autowired
    private BetaWhitelistService whitelistService;

    @Autowired
    private BetaWhitelistRepository whitelistRepo;

    @Autowired
    private UserRepository userRepo;

    @Test
    void approvingGrantsWhitelistAndRevokingTakesItAway() throws Exception {
        User user = seedUser("lifecycle_revoke", "13700009501", "LifecycleOne");

        // ① 管理员把资格改为「已通过」-> 白名单自动写入
        adminService.updateUser(user.getId(), betaStatus("approved"));

        List<BetaWhitelist> granted = whitelistRepo.findAllByQqOrderByCreatedAtAsc("13700009501");
        assertEquals(1, granted.size(), "审批通过必须自动产生白名单条目");
        assertEquals(BetaWhitelist.SOURCE_ACCOUNT, granted.get(0).getSource());
        assertTrue(verify("13700009501", "LifecycleOne").get("success").asBoolean(),
                "已获批的玩家必须能通过客户端校验");

        // ② 管理员取消资格 -> 条目随之消失，客户端随即被拒
        adminService.updateUser(user.getId(), betaStatus("none"));

        assertTrue(whitelistRepo.findAllByQqOrderByCreatedAtAsc("13700009501").isEmpty(),
                "取消内测资格后白名单必须同时消失，否则玩家照样能进游戏");
        assertFalse(verify("13700009501", "LifecycleOne").get("success").asBoolean(),
                "资格被取消后客户端校验必须不再放行");

        // ③ 重新批准 -> 条目自动回来（整个流程可逆、幂等）
        adminService.updateUser(user.getId(), betaStatus("approved"));
        assertTrue(verify("13700009501", "LifecycleOne").get("success").asBoolean());
    }

    @Test
    void changingIdentityWhileApprovedKeepsTheWhitelistInSync() throws Exception {
        User user = seedUser("lifecycle_identity", "13700009510", "BeforeNova");
        adminService.updateUser(user.getId(), betaStatus("approved"));
        assertTrue(verify("13700009510", "BeforeNova").get("success").asBoolean());

        // 管理员纠正玩家的游戏 ID（玩家自己改不了）-> 白名单必须跟着换，不能留下旧条目
        AdminUserUpdateRequest fix = new AdminUserUpdateRequest();
        fix.setMcId("AfterNova");
        adminService.updateUser(user.getId(), fix);

        assertFalse(verify("13700009510", "BeforeNova").get("success").asBoolean(),
                "旧游戏 ID 不能继续放行");
        assertTrue(verify("13700009510", "AfterNova").get("success").asBoolean(),
                "白名单必须自动跟到新的游戏 ID");
    }

    @Test
    void manuallyAddedWhitelistEntriesAreNeverTouchedByRevocation() throws Exception {
        // 运营手工给主播开的白名单，与账号审批无关
        BetaWhitelistRequest manual = new BetaWhitelistRequest();
        manual.setQq("13700009520");
        manual.setMcId("HostNova");
        manual.setNickname("主播");
        whitelistService.create(manual, "admin");

        User user = seedUser("lifecycle_manual", "13700009521", "PlayerNova");
        adminService.updateUser(user.getId(), betaStatus("approved"));
        assertEquals(1, whitelistRepo.findAllByQqOrderByCreatedAtAsc("13700009521").size());

        adminService.updateUser(user.getId(), betaStatus("denied"));

        assertTrue(whitelistRepo.findAllByQqOrderByCreatedAtAsc("13700009521").isEmpty());
        List<BetaWhitelist> manualRows = whitelistRepo.findAllByQqOrderByCreatedAtAsc("13700009520");
        assertEquals(1, manualRows.size(), "人工录入的白名单不能被连带删除");
        assertEquals(BetaWhitelist.SOURCE_MANUAL, manualRows.get(0).getSource());
        assertTrue(verify("13700009520", "HostNova").get("success").asBoolean(),
                "人工录入的条目应照常放行");
    }

    @Test
    void accountResyncRebuildsMissingEntriesAndClearsOnlySystemOrphans() {
        // ① 已获批、但白名单条目缺失（v3.1 之前的历史数据里很常见）：对账要补上
        User missing = seedUser("lifecycle_sync", "13700009530", "SyncNovaA");
        missing.setBetaStatus("approved");
        userRepo.save(missing);

        // ② 系统条目还在、账号却已不在「已通过」：对账要清掉
        whitelistService.syncApprovedPlayer("13700009531", "SyncNovaB", "历史遗留", null, "system");

        // ③ 手工录入的条目不属于账号体系：对账必须原样保留
        BetaWhitelistRequest manual = new BetaWhitelistRequest();
        manual.setQq("13700009532");
        manual.setMcId("ManualNova");
        whitelistService.create(manual, "admin");

        WhitelistSyncResult result = adminService.syncApprovedAccounts();

        assertTrue(result.getCreated() >= 1, "已获批玩家的缺失条目必须被补上");
        assertEquals(1, whitelistRepo.findAllByQqOrderByCreatedAtAsc("13700009530").size());
        assertTrue(whitelistRepo.findAllByQqOrderByCreatedAtAsc("13700009531").isEmpty(),
                "已失去资格的系统条目必须被清掉");
        assertEquals(1, whitelistRepo.findAllByQqOrderByCreatedAtAsc("13700009532").size(),
                "人工录入的条目不参与对账");
        assertTrue(result.getRemoved() >= 1, "应报告清理掉的孤儿条目数");
    }

    private AdminUserUpdateRequest betaStatus(String status) {
        AdminUserUpdateRequest request = new AdminUserUpdateRequest();
        request.setBetaStatus(status);
        return request;
    }

    private User seedUser(String username, String qq, String mcId) {
        User u = new User();
        u.setUsername(username);
        u.setPassword("not-used-by-this-test");
        u.setEmail(username + "@northstar.test");
        u.setQq(qq);
        u.setMcId(mcId);
        u.setBetaStatus("none");
        return userRepo.save(u);
    }

    private JsonNode verify(String qq, String name) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(
                        URI.create("http://127.0.0.1:" + port + "/api/beta/verify?qq=" + qq + "&name=" + name))
                .header("Accept", "application/json")
                .header("User-Agent", "NorthStarClientVerification/1.0")
                .GET()
                .build();
        HttpResponse<String> response =
                CLIENT.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        assertEquals(200, response.statusCode(), "校验接口应返回 200，实际 " + response.statusCode());
        return MAPPER.readTree(response.body());
    }
}
