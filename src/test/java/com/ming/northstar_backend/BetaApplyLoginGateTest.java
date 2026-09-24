package com.ming.northstar_backend;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ming.northstar_backend.dto.BetaPlanRequest;
import com.ming.northstar_backend.entity.BetaApplication;
import com.ming.northstar_backend.entity.User;
import com.ming.northstar_backend.repository.BetaApplicationRepository;
import com.ming.northstar_backend.repository.UserRepository;
import com.ming.northstar_backend.security.JwtUtil;
import com.ming.northstar_backend.service.BetaPlanService;
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
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 「内测申请必须登录平台账号」的真实 HTTP 回归测试。
 *
 * <p>规则有两层含义，缺一层都是漏洞：</p>
 * <ol>
 *   <li><b>没登录不能申请</b>。匿名请求必须被 Spring Security 挡在门口，而不是靠前端
 *       把按钮藏起来——前端只是提示，闸门必须在服务端。</li>
 *   <li><b>申请身份只能是登录的那个人</b>。申请记录直接挂在 {@code users.id} 上，
 *       QQ / 游戏 ID 从账号读取而非请求体，否则任何人都能拿别人的 QQ 替别人占名额。</li>
 * </ol>
 *
 * <p>顺带守住第三个前提：已登录但尚未申请 / 已申请待审 / 已通过，三种状态
 * 前端要能分辨，所以 {@code GET /api/beta/my-application} 也要被回归覆盖。</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class BetaApplyLoginGateTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final HttpClient CLIENT = HttpClient.newHttpClient();

    @Value("${local.server.port}")
    private int port;

    @Autowired
    private UserRepository userRepo;

    @Autowired
    private BetaApplicationRepository betaRepo;

    @Autowired
    private BetaPlanService betaPlanService;

    @Autowired
    private JwtUtil jwtUtil;

    @Test
    void anonymousRequestCannotApply() throws Exception {
        User user = seed("applygate_anon", "13800009001", "ApplyAnon");
        Long planId = openPlan("匿名门禁");

        HttpResponse<String> res = post("/api/beta/apply", null,
            "{\"reason\":\"veteran\",\"email\":\"anon@northstar.test\",\"planId\":" + planId + "}");

        assertTrue(res.statusCode() == 401 || res.statusCode() == 403,
            "未登录申请必须被拒，实际 " + res.statusCode());
        assertTrue(applicationsOf(user).isEmpty(), "被拒的请求不能在库里留下申请记录");
    }

    @Test
    void anonymousRequestCannotReadOwnApplication() throws Exception {
        HttpResponse<String> res = send("GET", "/api/beta/my-application", null, null);

        assertTrue(res.statusCode() == 401 || res.statusCode() == 403,
            "本人申请状态属于账号数据，未登录不能读，实际 " + res.statusCode());
    }

    @Test
    void applicationIdentityComesFromTheAccountNotFromTheRequestBody() throws Exception {
        User applicant = seed("applygate_identity", "13800009010", "ApplyNova");
        seed("applygate_victim", "13800009011", "VictimNova");
        Long planId = openPlan("身份取自账号");

        // 请求体里塞的是别人的账号标识：服务端必须完全忽略它
        String token = jwtUtil.generateToken(applicant.getId(), applicant.getUsername());
        JsonNode body = body(post("/api/beta/apply", token,
            "{\"query\":\"13800009011\",\"reason\":\"content\",\"email\":\"notify@northstar.test\",\"planId\":"
                + planId + "}"));

        assertEquals("申请提交成功", body.get("message").asText());

        List<BetaApplication> rows = applicationsOf(applicant);
        assertEquals(1, rows.size());
        BetaApplication app = rows.get(0);
        assertEquals(applicant.getId(), app.getUserId());
        assertEquals("applygate_identity", app.getUsername(), "用户名必须取自登录账号");
        assertEquals("13800009010", app.getQq(), "QQ 必须取自登录账号，不能被请求体覆盖");
        assertEquals("ApplyNova", app.getMcId(), "游戏 ID 必须取自登录账号");
        assertEquals("pending", app.getStatus());
        assertEquals("notify@northstar.test", app.getEmail(),
            "通知邮箱允许玩家单独填写（与账号邮箱可以不同）");

        assertTrue(applicationsOf(userRepo.findByUsername("applygate_victim").orElseThrow()).isEmpty(),
            "不能替别人产生申请记录");
    }

    @Test
    void myApplicationReportsNoneThenPendingThenApproved() throws Exception {
        User user = seed("applygate_states", "13800009020", "StatesNova");
        Long planId = openPlan("状态流转");
        String token = jwtUtil.generateToken(user.getId(), user.getUsername());

        // ① 全新的账号：没有任何申请，前端据此显示申请表单
        JsonNode none = body(send("GET", "/api/beta/my-application", token, null));
        assertFalse(none.get("data").get("hasApplication").asBoolean());
        assertEquals("none", none.get("data").get("betaStatus").asText());

        // ② 提交之后：审核中，前端据此把表单换成状态卡
        body(post("/api/beta/apply", token,
            "{\"reason\":\"tester\",\"email\":\"states@northstar.test\",\"planId\":" + planId + "}"));

        JsonNode pending = body(send("GET", "/api/beta/my-application", token, null));
        assertTrue(pending.get("data").get("hasApplication").asBoolean());
        assertEquals("pending", pending.get("data").get("status").asText());
        assertEquals(planId, pending.get("data").get("planId").asLong());
        assertEquals("状态流转", pending.get("data").get("planName").asText(),
            "前端要显示申请的是哪个计划");
        assertFalse(pending.get("data").get("createdAt").asText().isEmpty());

        // ③ 管理员批准后：资格到手
        user.setBetaStatus("approved");
        userRepo.save(user);

        JsonNode approved = body(send("GET", "/api/beta/my-application", token, null));
        assertEquals("approved", approved.get("data").get("betaStatus").asText());
    }

    @Test
    void aSecondApplicationIsRejectedWhileTheFirstIsStillPending() throws Exception {
        User user = seed("applygate_dup", "13800009030", "DupNova");
        Long planId = openPlan("重复申请");
        String token = jwtUtil.generateToken(user.getId(), user.getUsername());
        String payload = "{\"reason\":\"other\",\"email\":\"dup@northstar.test\",\"planId\":" + planId + "}";

        assertEquals(200, post("/api/beta/apply", token, payload).statusCode());

        HttpResponse<String> second = post("/api/beta/apply", token, payload);
        assertEquals(400, second.statusCode(), "审核期间不能重复提交");
        assertEquals("你已提交过申请，请等待审核",
            MAPPER.readTree(second.body()).get("message").asText());
        assertEquals(1, applicationsOf(user).size(), "重复提交不能多出一条申请");
    }

    @Test
    void anAlreadyApprovedAccountCannotApplyAgain() throws Exception {
        User user = seed("applygate_approved", "13800009040", "GrantedNova");
        user.setBetaStatus("approved");
        userRepo.save(user);
        Long planId = openPlan("已有资格");

        HttpResponse<String> res = post("/api/beta/apply",
            jwtUtil.generateToken(user.getId(), user.getUsername()),
            "{\"reason\":\"other\",\"email\":\"granted@northstar.test\",\"planId\":" + planId + "}");

        assertEquals(400, res.statusCode());
        assertEquals("你已拥有内测资格", MAPPER.readTree(res.body()).get("message").asText());
        assertTrue(applicationsOf(user).isEmpty());
    }

    // ---------- helpers ----------

    private User seed(String username, String qq, String mcId) {
        User u = new User();
        u.setUsername(username);
        u.setPassword("not-used-by-this-test");   // 直接签 JWT，不走密码登录
        u.setEmail(username + "@northstar.test");
        u.setQq(qq);
        u.setMcId(mcId);
        u.setBetaStatus("none");
        return userRepo.save(u);
    }

    /** 建一个当下就能申请的计划：状态 active 且日期窗口覆盖今天。 */
    private Long openPlan(String name) {
        BetaPlanRequest request = new BetaPlanRequest();
        request.setName(name);
        request.setPhase("P1");
        request.setDescription("测试用计划");
        request.setStartsOn(LocalDate.now().minusDays(1));
        request.setEndsOn(LocalDate.now().plusDays(30));
        request.setCapacity(500);
        Long id = betaPlanService.createPlan(request).getId();
        betaPlanService.updatePlanStatus(id, "active");
        return id;
    }

    private List<BetaApplication> applicationsOf(User user) {
        return betaRepo.findByUserIdOrderByCreatedAtDesc(user.getId());
    }

    private JsonNode body(HttpResponse<String> response) throws Exception {
        assertEquals(200, response.statusCode(), response.body());
        return MAPPER.readTree(response.body());
    }

    private HttpResponse<String> post(String path, String token, String json) throws Exception {
        return send("POST", path, token, json);
    }

    private HttpResponse<String> send(String method, String path, String token, String json) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
            .header("Accept", "application/json");
        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }
        if (json == null) {
            builder.method(method, HttpRequest.BodyPublishers.noBody());
        } else {
            builder.header("Content-Type", "application/json")
                .method(method, HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8));
        }
        return CLIENT.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }
}
