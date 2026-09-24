package com.ming.northstar_backend.support;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

/**
 * QQ 号格式的唯一判定入口。
 *
 * <p>「QQ + 游戏ID」是本项目校验玩家身份的两项依据。QQ 会出现在注册、资料修改、
 * 内测申请、白名单、校验接口等多处，格式判定必须只有一处实现——否则很容易出现
 * 「注册能过、白名单导入却报错」这类不一致。</p>
 *
 * <p>正则来自配置项 {@code northstar.verify.qq-pattern}，默认值与客户端 Mod 的
 * {@code qqPattern} 保持一致（首位非 0 的 5-11 位数字）。</p>
 */
@Component
public class QqFormat {

    /** 默认格式，与客户端 Mod 默认配置一致。 */
    public static final String DEFAULT_PATTERN = "^[1-9]\\d{4,10}$";

    private final Pattern pattern;

    public QqFormat(@Value("${northstar.verify.qq-pattern:" + DEFAULT_PATTERN + "}") String qqPattern) {
        this.pattern = Pattern.compile(qqPattern);
    }

    /** 去除首尾空白；{@code null} 归一化为空串。 */
    public String normalize(String raw) {
        return raw == null ? "" : raw.trim();
    }

    /** 归一化后是否是一个合法的 QQ 号。 */
    public boolean isValid(String raw) {
        String value = normalize(raw);
        return !value.isEmpty() && pattern.matcher(value).matches();
    }

    /** 归一化并校验，非法时抛出运行时异常（Service 层会转成 400）。 */
    public String require(String raw, String fieldLabel) {
        String value = normalize(raw);
        if (!value.isEmpty() && !pattern.matcher(value).matches()) {
            throw new RuntimeException(fieldLabel + "格式无效");
        }
        return value;
    }
}
