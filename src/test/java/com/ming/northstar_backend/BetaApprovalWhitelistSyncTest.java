package com.ming.northstar_backend;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ming.northstar_backend.entity.BetaWhitelist;
import com.ming.northstar_backend.repository.BetaWhitelistRepository;
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
 * 「注册平台提交 QQ + 离线服游戏ID → 审批通过 → 客户端校验放行」这条链路的回归测试。
 *
 * <p>之所以必须有这个测试：白名单原先完全靠运营手工维护，与注册 / 审批数据零联动，
 * 于是会出现「玩家在平台注册并被批准，进游戏却被判未通过而崩溃」。这里从审批同步出发，
 * 用真实 HTTP 打通 {@code /api/beta/verify}，确保链路真的接上了。</p>
 *
 * <p>QQ 只用与其它测试类不冲突的号段，避免共享 H2 上下文互相干扰。</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class BetaApprovalWhitelistSyncTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final HttpClient CLIENT = HttpClient.newHttpClient();

    @Value("${local.server.port}")
    private int port;

    @Autowired
    private BetaWhitelistService whitelistService;

    @Autowired
    private BetaWhitelistRepository whitelistRepo;

    @Test
    void approvedPlayerEntersWhitelistAndPassesVerification() throws Exception {
        // 玩家在注册平台提交的 QQ 与离线服 ID；审批通过后由系统同步成白名单
        BetaWhitelistService.SyncResult result = whitelistService.syncApprovedPlayer(
                "13600009001", "NovaSteve", "明明", "内测审批自动同步", "system:beta-approval");

        assertTrue(result.succeeded(), "审批同步失败就意味着玩家一进游戏就崩溃");
        assertEquals(BetaWhitelistService.SyncOutcome.CREATED, result.outcome());

        List<BetaWhitelist> rows = whitelistRepo.findAllByQqOrderByCreatedAtAsc("13600009001");
        assertEquals(1, rows.size());
        assertEquals(BetaWhitelist.SOURCE_ACCOUNT, rows.get(0).getSource());

        // 客户端真实请求：QQ + 提交的游戏ID -> 通过
        JsonNode pass = body("/api/beta/verify?qq=13600009001&name=NovaSteve");
        assertTrue(pass.get("success").asBoolean(), "已批准玩家必须能通过校验");
        assertEquals(0, pass.get("code").asInt());
        assertEquals("bound", pass.get("data").get("matchedBy").asText());

        // 换一个游戏ID -> 明确未通过，防止资格被转给别人
        assertFalse(body("/api/beta/verify?qq=13600009001&name=SomeoneElse")
                .get("success").asBoolean());
    }

    @Test
    void repeatedApprovalDoesNotDuplicateEntries() throws Exception {
        whitelistService.syncApprovedPlayer("13600009002", "NovaAlex", "老王", null, "system");
        whitelistService.syncApprovedPlayer("13600009002", "NovaAlex", "老王", null, "system");

        assertEquals(1, whitelistRepo.findAllByQqOrderByCreatedAtAsc("13600009002").size(),
                "重复审批不能产生重复条目");
        assertTrue(body("/api/beta/verify?qq=13600009002&name=NovaAlex").get("success").asBoolean());
    }

    @Test
    void playerWithoutQqCannotBeLiftedIntoWhitelist() {
        BetaWhitelistService.SyncResult result = whitelistService.syncApprovedPlayer(
                null, "NovaNobody", "无QQ", null, "system");

        assertEquals(BetaWhitelistService.SyncOutcome.MISSING_QQ, result.outcome());
        assertFalse(result.succeeded());
    }

    private JsonNode body(String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
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
