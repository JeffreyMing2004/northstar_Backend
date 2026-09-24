package com.ming.northstar_backend.dto;

/**
 * 内测资格校验接口（接口 A）的响应体。
 *
 * <p><b>这是与客户端 Mod 的强契约，字段名与取值不可随意改动。</b>
 * 客户端 Mod（{@code RemoteVerifier.readSuccessFlag}）的判定规则：</p>
 *
 * <ol>
 *   <li>HTTP 非 2xx → 视为「服务不可用」，客户端不解析响应体，玩家可重试</li>
 *   <li>存在 {@code success} 字段 → 以它为准：JSON 布尔 {@code true} 通过、{@code false} 未通过
 *       （客户端会生成崩溃报告并退出游戏）</li>
 *   <li>不存在 {@code success}，但存在 {@code code} → {@code code == 0} 或 {@code code == 200} 通过，
 *       其它值未通过</li>
 *   <li>两个字段都没有、或响应体不是 JSON 对象 → 视为「服务不可用」</li>
 * </ol>
 *
 * <p>因此本类<b>同时</b>输出 {@code success} 与 {@code code}：{@code success} 命中第 2 条，
 * {@code code} 作为兼容后端既有 {@code ApiResponse}（用 {@code 200} 表示成功）语义的兜底。</p>
 */
public class BetaVerifyResponse {

    /** 未通过的业务码。 */
    public static final int CODE_REJECT = 1001;
    /** 通过的业务码。 */
    public static final int CODE_PASS = 0;
    /** 参数非法的业务码（配合 HTTP 400 返回）。 */
    public static final int CODE_BAD_REQUEST = 4000;
    /** 服务异常的业务码（配合 HTTP 5xx 返回）。 */
    public static final int CODE_SERVER_ERROR = 5000;
    /** 被限流的业务码（配合 HTTP 429 返回）。 */
    public static final int CODE_TOO_MANY_REQUESTS = 4290;

    private boolean success;
    private int code;
    private String msg;
    private BetaVerifyData data;

    public BetaVerifyResponse() {
    }

    public static BetaVerifyResponse pass(BetaVerifyData data) {
        BetaVerifyResponse response = new BetaVerifyResponse();
        response.success = true;
        response.code = CODE_PASS;
        response.msg = "验证通过";
        response.data = data;
        return response;
    }

    public static BetaVerifyResponse reject(String msg) {
        BetaVerifyResponse response = new BetaVerifyResponse();
        response.success = false;
        response.code = CODE_REJECT;
        response.msg = msg;
        response.data = null;
        return response;
    }

    public static BetaVerifyResponse badRequest(String msg) {
        BetaVerifyResponse response = new BetaVerifyResponse();
        response.success = false;
        response.code = CODE_BAD_REQUEST;
        response.msg = msg;
        return response;
    }

    public static BetaVerifyResponse serverError(String msg) {
        BetaVerifyResponse response = new BetaVerifyResponse();
        response.success = false;
        response.code = CODE_SERVER_ERROR;
        response.msg = msg;
        return response;
    }

    public static BetaVerifyResponse tooManyRequests(String msg) {
        BetaVerifyResponse response = new BetaVerifyResponse();
        response.success = false;
        response.code = CODE_TOO_MANY_REQUESTS;
        response.msg = msg;
        return response;
    }

    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }
    public int getCode() { return code; }
    public void setCode(int code) { this.code = code; }
    public String getMsg() { return msg; }
    public void setMsg(String msg) { this.msg = msg; }
    public BetaVerifyData getData() { return data; }
    public void setData(BetaVerifyData data) { this.data = data; }
}
