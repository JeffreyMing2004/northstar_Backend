package com.ming.northstar_backend;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ming.northstar_backend.entity.BetaWhitelist;
import com.ming.northstar_backend.repository.BetaWhitelistRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 真实 HTTP 端到端测试：把应用跑起来，用 JDK HttpClient 打真实请求。
 *
 * <p>覆盖只有真跑一遍才能确认的事：</p>
 * <ol>
 *   <li>{@code /api/beta/verify} 在 {@code SecurityConfig} 下确实**匿名可达**</li>
 *   <li>响应体在真实 Jackson 配置下就是 {@code {"success":..,"code":..}} 这个形状</li>
 *   <li>「QQ + 游戏ID」的判定分支在真实请求下符合预期（离线模式不使用 UUID）</li>
 *   <li>管理接口确实**不可匿名访问**</li>
 * </ol>
 *
 * <p>使用 H2（test profile），不会连生产 MySQL。</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class BetaVerifyEndToEndTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final HttpClient CLIENT = HttpClient.newHttpClient();

    @Value("${local.server.port}")
    private int port;

    @Autowired
    private BetaWhitelistRepository whitelistRepo;

    @Test
    void verifyEndpointIsPublicAndHonoursTheContract() throws Exception {
        seed("13600000001", "Steve", 1, null);   // 绑定游戏ID
        seed("13600000006", null, 1, null);      // 只绑 QQ，不限定游戏ID

        // 1) QQ + 游戏ID 完全一致 -> 通过
        HttpResponse<String> pass = get("/api/beta/verify?qq=13600000001&name=Steve");
        assertEquals(200, pass.statusCode());
        assertTrue(pass.headers().firstValue("Content-Type").orElse("").contains("application/json"));
        JsonNode passBody = MAPPER.readTree(pass.body());
        assertTrue(passBody.get("success").asBoolean(), "白名单内必须通过");
        assertEquals(0, passBody.get("code").asInt());
        assertEquals("13600000001", passBody.get("data").get("qq").asText());
        assertEquals("Steve", passBody.get("data").get("gameId").asText());
        assertEquals("bound", passBody.get("data").get("matchedBy").asText());

        // 2) 游戏ID 大小写不同 -> 仍然通过
        assertTrue(MAPPER.readTree(get("/api/beta/verify?qq=13600000001&name=steve").body())
                .get("success").asBoolean(), "游戏ID 比较应忽略大小写");

        // 3) 游戏ID 与绑定不一致 -> 200 + success:false（客户端据此生成崩溃报告）
        JsonNode mismatch = MAPPER.readTree(get("/api/beta/verify?qq=13600000001&name=Alex").body());
        assertFalse(mismatch.get("success").asBoolean(), "游戏ID 不匹配必须判为未通过");
        assertEquals(1001, mismatch.get("code").asInt());

        // 4) 白名单里没有这个 QQ -> 200 + success:false
        JsonNode reject = MAPPER.readTree(get("/api/beta/verify?qq=13600000002&name=Steve").body());
        assertFalse(reject.get("success").asBoolean());
        assertEquals(1001, reject.get("code").asInt());

        // 5) 未绑定游戏ID 的条目 -> 任意游戏ID 放行，并标注 matchedBy=unbound
        JsonNode unbound = MAPPER.readTree(get("/api/beta/verify?qq=13600000006&name=Whatever").body());
        assertTrue(unbound.get("success").asBoolean());
        assertTrue(unbound.get("data").get("gameId").isNull());
        assertEquals("unbound", unbound.get("data").get("matchedBy").asText());

        // 6) 中文游戏名必须能正确解码
        HttpResponse<String> chinese = get("/api/beta/verify?qq=13600000006&name="
                + URLEncoder.encode("明明 老师", StandardCharsets.UTF_8));
        assertEquals(200, chinese.statusCode());
        assertEquals("明明 老师",
                MAPPER.readTree(chinese.body()).get("data").get("playerName").asText());

        // 7) 旧版客户端多传的 uuid 必须被忽略（Spring 默认忽略未知查询参数）
        assertTrue(MAPPER.readTree(
                        get("/api/beta/verify?qq=13600000001&name=Steve&uuid=069a79f4-44e9-4726-a5be-fca90e38aaf5").body())
                .get("success").asBoolean(), "多余的 uuid 参数不应影响判定");

        // 8) QQ 格式非法 -> 400（客户端可重试，不崩溃）
        assertEquals(400, get("/api/beta/verify?qq=abc&name=Steve").statusCode());

        // 9) 缺少 name -> 400（离线模式下没有游戏ID 无法判定，但不能判成「未通过」）
        assertEquals(400, get("/api/beta/verify?qq=13600000001").statusCode());

        // 10) 禁用 -> 明确未通过
        seed("13600000003", null, 0, null);
        JsonNode disabled = MAPPER.readTree(get("/api/beta/verify?qq=13600000003&name=Steve").body());
        assertFalse(disabled.get("success").asBoolean());
        assertEquals(1001, disabled.get("code").asInt());

        // 11) 过期 -> 明确未通过
        seed("13600000004", null, 1, LocalDateTime.now().minusMinutes(1));
        assertFalse(MAPPER.readTree(get("/api/beta/verify?qq=13600000004&name=Steve").body())
                .get("success").asBoolean());

        // 12) 未过期 -> 通过
        seed("13600000005", null, 1, LocalDateTime.now().plusDays(1));
        assertTrue(MAPPER.readTree(get("/api/beta/verify?qq=13600000005&name=Steve").body())
                .get("success").asBoolean());
    }

    @Test
    void oneQqMayBindSeveralGameIds() throws Exception {
        seed("13600000010", "Steve", 1, null);
        seed("13600000010", "Alex", 1, null);

        assertTrue(MAPPER.readTree(get("/api/beta/verify?qq=13600000010&name=Steve").body())
                .get("success").asBoolean());
        assertTrue(MAPPER.readTree(get("/api/beta/verify?qq=13600000010&name=alex").body())
                .get("success").asBoolean());
        assertFalse(MAPPER.readTree(get("/api/beta/verify?qq=13600000010&name=Notch").body())
                .get("success").asBoolean(), "两个绑定都不匹配时必须拒绝");
    }

    @Test
    void adminWhitelistEndpointRequiresAuthentication() throws Exception {
        HttpResponse<String> response = get("/api/admin/beta/whitelist");

        assertTrue(response.statusCode() == 401 || response.statusCode() == 403,
                "管理接口不能匿名访问，实际返回 " + response.statusCode());
    }

    @Test
    void publicBetaCheckEndpointStillWorks() throws Exception {
        // 确认新增 permitAll 规则没有把既有的 /api/beta/check 挤掉
        String query = URLEncoder.encode("没有这个账号", StandardCharsets.UTF_8);
        assertEquals(200, get("/api/beta/check?query=" + query).statusCode());
    }

    /** 按 (qq, 游戏ID) upsert 一条白名单记录。 */
    private void seed(String qq, String gameId, int status, LocalDateTime expireAt) {
        BetaWhitelist entry = whitelistRepo.findAllByQqOrderByCreatedAtAsc(qq).stream()
                .filter(row -> Objects.equals(row.getBoundGameId(), gameId))
                .findFirst()
                .orElseGet(BetaWhitelist::new);
        entry.setQq(qq);
        entry.setMcId(gameId);
        entry.setStatus(status);
        entry.setExpireAt(expireAt);
        entry.setRemark("IT");
        whitelistRepo.save(entry);
    }

    private HttpResponse<String> get(String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .header("Accept", "application/json")
                .header("User-Agent", "NorthStarClientVerification/1.0")
                .GET()
                .build();
        return CLIENT.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }
}
