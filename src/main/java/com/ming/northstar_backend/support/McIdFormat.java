package com.ming.northstar_backend.support;

import java.util.regex.Pattern;

/**
 * Minecraft ID（离线服游戏 ID）格式的唯一判定入口。
 *
 * <p>与 {@link QqFormat} 同为「内测资格凭据」的格式判定，区别在于本类没有配置项：
 * 游戏 ID 的合法性由服务端本身（离线服登录名）决定，不随部署环境变化，
 * 因此做成静态工具类，任何地方都能直接用，不需要注入。</p>
 *
 * <p>在 v3.3 之前，「3-16 位字母数字下划线」这条规则在注册、绑定游戏账号、
 * 后台编辑玩家、后台发放内测、白名单录入里各写了一份，很容易出现
 * 「注册能过、白名单导入却报错」这类不一致。现在全部收敛到本类。</p>
 */
public final class McIdFormat {

    /** 与客户端登录名一致的格式：3-16 位字母、数字或下划线。 */
    public static final String DEFAULT_PATTERN = "^[a-zA-Z0-9_]{3,16}$";

    private static final Pattern PATTERN = Pattern.compile(DEFAULT_PATTERN);

    private McIdFormat() {}

    /** 去除首尾空白；{@code null} 归一化为空串。 */
    public static String normalize(String raw) {
        return raw == null ? "" : raw.trim();
    }

    /** 归一化后是否是一个合法的 Minecraft ID（按「不限定游戏 ID」的语义，空串不算合法）。 */
    public static boolean isValid(String raw) {
        String value = normalize(raw);
        return !value.isEmpty() && PATTERN.matcher(value).matches();
    }

    /**
     * 归一化并校验。
     *
     * <p>空值视为「不限定游戏 ID / 本次不提交」直接放行并返回空串——是否允许为空由调用方决定，
     * 例如白名单条目允许只绑 QQ，而绑定游戏账号接口会另行要求非空。</p>
     *
     * @param fieldLabel 出现错误时的字段名，例如 {@code "Minecraft ID"} 或 {@code "游戏ID"}
     */
    public static String require(String raw, String fieldLabel) {
        String value = normalize(raw);
        if (!value.isEmpty() && !PATTERN.matcher(value).matches()) {
            throw new RuntimeException(fieldLabel + " 格式无效（3-16 位字母、数字或下划线）");
        }
        return value;
    }

    /** 归一化并校验，且要求必须非空。 */
    public static String requirePresent(String raw, String fieldLabel) {
        String value = normalize(raw);
        if (value.isEmpty()) {
            throw new RuntimeException(fieldLabel + "不能为空");
        }
        return require(value, fieldLabel);
    }
}
