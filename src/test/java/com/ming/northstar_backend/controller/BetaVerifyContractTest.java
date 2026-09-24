package com.ming.northstar_backend.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ming.northstar_backend.dto.BetaVerifyData;
import com.ming.northstar_backend.dto.BetaVerifyResponse;
import com.ming.northstar_backend.service.BetaPlanService;
import com.ming.northstar_backend.service.BetaService;
import com.ming.northstar_backend.service.BetaWhitelistService;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 与 Minecraft 客户端 Mod 的接口契约测试。
 *
 * <p>客户端 {@code RemoteVerifier.readSuccessFlag} 的解析规则：先看 {@code success}
 * （JSON 布尔），再看 {@code code}（0 或 200 为通过）。本测试锁死这两处的序列化结果——
 * 一旦有人把 {@code isSuccess()} 改名或改用 {@code ApiResponse} 包装，测试会立刻失败。</p>
 *
 * <p>同时锁死「身份只用 QQ + 游戏ID」这一点：{@code verifyBeta} 不再接受 {@code uuid}，
 * 服务层签名也随之固定为 4 个参数。</p>
 */
class BetaVerifyContractTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    private BetaWhitelistService stubService() {
        return mock(BetaWhitelistService.class);
    }

    private BetaController controller(BetaWhitelistService service) {
        return new BetaController(mock(BetaService.class), mock(BetaPlanService.class), service);
    }

    @Test
    void passResponseSerializesSuccessBooleanAndCodeZero() throws Exception {
        BetaVerifyData data = new BetaVerifyData();
        data.setQq("123456789");
        data.setNickname("明明");
        data.setGameId("Steve");
        data.setMatchedBy("bound");

        JsonNode json = objectMapper.readTree(
                objectMapper.writeValueAsString(BetaVerifyResponse.pass(data)));

        assertTrue(json.has("success"), "客户端只认 success 字段名");
        assertFalse(json.has("isSuccess"), "不能序列化成 isSuccess");
        assertTrue(json.get("success").isBoolean(), "success 必须是 JSON 布尔，不能用数字 1");
        assertTrue(json.get("success").asBoolean());
        assertEquals(0, json.get("code").asInt());
        assertEquals("123456789", json.get("data").get("qq").asText());
        assertEquals("Steve", json.get("data").get("gameId").asText());
        assertEquals("bound", json.get("data").get("matchedBy").asText());
    }

    @Test
    void rejectResponseSerializesSuccessFalseAndCode1001() throws Exception {
        JsonNode json = objectMapper.readTree(
                objectMapper.writeValueAsString(BetaVerifyResponse.reject("该 QQ 未获得内测资格")));

        assertTrue(json.get("success").isBoolean());
        assertFalse(json.get("success").asBoolean());
        assertEquals(1001, json.get("code").asInt());
        assertTrue(json.get("data").isNull());
    }

    @Test
    void controllerMapsOutcomeStatusCodesOntoHttpResponse() {
        BetaWhitelistService service = stubService();

        when(service.verify(any(), any(), any(), any())).thenReturn(
                new BetaWhitelistService.VerifyOutcome(200, BetaVerifyResponse.pass(new BetaVerifyData())),
                new BetaWhitelistService.VerifyOutcome(200, BetaVerifyResponse.reject("未通过")),
                new BetaWhitelistService.VerifyOutcome(400, BetaVerifyResponse.badRequest("格式无效")),
                new BetaWhitelistService.VerifyOutcome(429, BetaVerifyResponse.tooManyRequests("太频繁")),
                new BetaWhitelistService.VerifyOutcome(500, BetaVerifyResponse.serverError("内部错误")));

        BetaController controller = controller(service);
        MockHttpServletRequest request = new MockHttpServletRequest();

        assertEquals(200, controller.verifyBeta("123456789", "Steve", request).getStatusCode().value());
        assertEquals(200, controller.verifyBeta("123456789", "Alex", request).getStatusCode().value());
        assertEquals(400, controller.verifyBeta("bad", "Steve", request).getStatusCode().value());
        assertEquals(429, controller.verifyBeta("123456789", "Steve", request).getStatusCode().value());
        assertEquals(500, controller.verifyBeta("123456789", "Steve", request).getStatusCode().value());
    }

    @Test
    void controllerForwardsQqAndGameIdToTheService() {
        BetaWhitelistService service = stubService();
        when(service.verify(any(), any(), any(), any()))
                .thenReturn(new BetaWhitelistService.VerifyOutcome(200, BetaVerifyResponse.reject("x")));

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.1.2.3");
        request.addHeader("User-Agent", "NorthStarClientVerification/1.0");

        controller(service).verifyBeta("123456789", "Steve", request);

        // 只上报 QQ 与游戏ID，没有第三个身份参数（离线模式下 UUID 无意义）
        org.mockito.Mockito.verify(service).verify("123456789", "Steve", "10.1.2.3",
                "NorthStarClientVerification/1.0");
    }

    @Test
    void rejectOutcomeStillReturnsHttp200SoClientReadsBody() {
        BetaWhitelistService service = stubService();
        when(service.verify(anyString(), any(), any(), any()))
                .thenReturn(new BetaWhitelistService.VerifyOutcome(200, BetaVerifyResponse.reject("未通过")));

        ResponseEntity<BetaVerifyResponse> response =
                controller(service).verifyBeta("123450000", "Steve", new MockHttpServletRequest());

        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertFalse(response.getBody().isSuccess());
    }

    @Test
    void resolvesRealClientIpFromProxyHeaders() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("127.0.0.1");
        request.addHeader("X-Forwarded-For", "203.0.113.9, 10.0.0.1");
        assertEquals("203.0.113.9", BetaController.resolveClientIp(request));

        MockHttpServletRequest realIp = new MockHttpServletRequest();
        realIp.setRemoteAddr("127.0.0.1");
        realIp.addHeader("X-Real-IP", "198.51.100.7");
        assertEquals("198.51.100.7", BetaController.resolveClientIp(realIp));

        MockHttpServletRequest direct = new MockHttpServletRequest();
        direct.setRemoteAddr("127.0.0.1");
        assertEquals("127.0.0.1", BetaController.resolveClientIp(direct));
    }
}
