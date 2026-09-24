package com.ming.northstar_backend;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ming.northstar_backend.dto.AdminUserUpdateRequest;
import com.ming.northstar_backend.dto.UserDto;
import com.ming.northstar_backend.entity.User;
import com.ming.northstar_backend.repository.UserRepository;
import com.ming.northstar_backend.security.JwtUtil;
import com.ming.northstar_backend.service.AdminService;
import com.ming.northstar_backend.support.QqBinding;
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
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 「个人设置里绑定 QQ，但一个账号只允许绑一次」的真实 HTTP 回归测试。
 *
 * <p>之所以要走真实 Tomcat + H2 而不是只测 service：规则的最后一道门是
 * {@code PUT /api/auth/profile} 这条接口，只要它把异常吞掉换成 500，
 * 前端就拿不到「已绑定不可更改」这句话，玩家只会看到一句无意义的「更新失败」。
 * 所以这里同时校验状态码、消息原文和数据库里的值。</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class QqBindingEndToEndTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final HttpClient CLIENT = HttpClient.newHttpClient();

    @Value("${local.server.port}")
    private int port;

    @Autowired
    private UserRepository userRepo;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private AdminService adminService;

    @Test
    void playerBindsQqOnceAndCanNeverChangeIt() throws Exception {
        User user = seed("qqbind_once", null);
        String token = jwtUtil.generateToken(user.getId(), user.getUsername());

        // 1) 未绑定 -> 首次绑定成功，并记下绑定时间
        JsonNode first = body(putProfile(token, "{\"qq\":\"13600007001\"}"));
        assertEquals(200, first.get("code").asInt(), first.toString());
        assertEquals("13600007001", first.get("data").get("qq").asText());
        assertFalse(first.get("data").get("qqBoundAt").isNull(), "首次绑定必须记录绑定时间");
        LocalDateTime boundAt = reload(user).getQqBoundAt();
        assertNotNull(boundAt);

        // 2) 换一个 QQ -> 400 + 原话，且库里纹丝不动
        HttpResponse<String> denied = putProfile(token, "{\"qq\":\"13600007002\"}");
        assertEquals(400, denied.statusCode(), "改绑必须被拒绝");
        assertEquals(QqBinding.ALREADY_BOUND_MESSAGE,
                MAPPER.readTree(denied.body()).get("message").asText());
        assertEquals("13600007001", reload(user).getQq(), "被拒绝后不能让值变掉");
        assertEquals(boundAt, reload(user).getQqBoundAt());

        // 3) 重复提交同一个 QQ -> 幂等成功（玩家重复点保存不该报错）
        assertEquals(200, body(putProfile(token, "{\"qq\":\"13600007001\"}")).get("code").asInt());

        // 4) 提交空串 -> 既不改也不解绑
        assertEquals(200, body(putProfile(token, "{\"qq\":\"\"}")).get("code").asInt());
        assertEquals("13600007001", reload(user).getQq(), "空值不能把已绑定的 QQ 抹掉");
    }

    @Test
    void malformedQqIsRejectedBeforeItConsumesTheOnlyBinding() throws Exception {
        User user = seed("qqbind_bad", null);
        String token = jwtUtil.generateToken(user.getId(), user.getUsername());

        HttpResponse<String> res = putProfile(token, "{\"qq\":\"abc123\"}");

        assertEquals(400, res.statusCode());
        assertNull(reload(user).getQq(), "格式非法不能占用掉唯一的一次绑定机会");
        assertNull(reload(user).getQqBoundAt());
    }

    @Test
    void adminIsStillAbleToCorrectATypo() {
        // 玩家注册时把 QQ 打错了，自己是改不了的；管理员必须还能救回来，
        // 否则这个账号会永久卡在「资格校验填的 QQ 与白名单不一致」上。
        User user = seed("qqbind_admin", "13600007010");
        user.setQqBoundAt(LocalDateTime.now());
        userRepo.save(user);

        AdminUserUpdateRequest req = new AdminUserUpdateRequest();
        req.setQq("13600007011");
        UserDto updated = adminService.updateUser(user.getId(), req);

        assertEquals("13600007011", updated.getQq());
        assertEquals("13600007011", reload(user).getQq());
        assertNotNull(reload(user).getQqBoundAt(), "纠错不该丢掉绑定时间");
    }

    private User seed(String username, String qq) {
        User u = new User();
        u.setUsername(username);
        u.setPassword("not-used-by-this-test");   // 直接签 JWT，不走密码登录
        u.setEmail(username + "@northstar.test");
        u.setQq(qq);
        u.setQqBoundAt(qq == null ? null : LocalDateTime.now());
        u.setBetaStatus("none");
        return userRepo.save(u);
    }

    private User reload(User user) {
        return userRepo.findById(user.getId()).orElseThrow();
    }

    private JsonNode body(HttpResponse<String> response) throws Exception {
        assertEquals(200, response.statusCode(), response.body());
        return MAPPER.readTree(response.body());
    }

    private HttpResponse<String> putProfile(String token, String json) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(
                        URI.create("http://127.0.0.1:" + port + "/api/auth/profile"))
                .header("Authorization", "Bearer " + token)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                .build();
        return CLIENT.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }
}
