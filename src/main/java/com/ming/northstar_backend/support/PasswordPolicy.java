package com.ming.northstar_backend.support;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 密码强度策略的唯一实现。
 *
 * <p>注册、重置密码、（后台）改密必须走同一套规则：渗透报告 NS-10 指出的正是
 * 「注册完全不校验、只有重置要求 6 位」这种不一致——攻击者注册一个弱口令账号
 * 即可拿它当跳板（例如撞库成功后再走 NS-03 接管他人账号）。</p>
 *
 * <p>规则：</p>
 * <ol>
 *   <li>长度 {@code [minLength, 72]} —— 上限 72 是因为 BCrypt 只取前 72 字节，
 *       更长的口令不会增加强度，反而容易让用户误以为「更长更安全」。</li>
 *   <li>至少包含一个字母与一个数字。</li>
 *   <li>不允许包含空白字符（避免前后空格被误删导致登录不上）。</li>
 * </ol>
 */
@Component
public class PasswordPolicy {

    /** BCrypt 有效输入上限（字节）。 */
    public static final int MAX_LENGTH = 72;
    private static final int HARD_MIN_LENGTH = 6;

    private final int minLength;

    public PasswordPolicy(@Value("${northstar.auth.password-min-length:8}") int minLength) {
        // 即便配置被人为调小，也不允许低于 6：那是修复前的历史下限
        this.minLength = Math.min(Math.max(minLength, HARD_MIN_LENGTH), MAX_LENGTH);
    }

    public int getMinLength() {
        return minLength;
    }

    /** 规则的人话描述，直接回给前端展示。 */
    public String describe() {
        return "密码长度需为 " + minLength + "-" + MAX_LENGTH + " 位，且同时包含字母和数字";
    }

    /** 校验失败抛 {@link RuntimeException}（Controller 层转 400）。 */
    public void validate(String raw, String fieldLabel) {
        String label = fieldLabel == null || fieldLabel.isBlank() ? "密码" : fieldLabel;
        String value = raw == null ? "" : raw;
        if (value.isEmpty()) {
            throw new RuntimeException("请输入" + label);
        }
        if (value.length() < minLength) {
            throw new RuntimeException(label + "长度不能少于" + minLength + "位");
        }
        if (value.length() > MAX_LENGTH) {
            throw new RuntimeException(label + "长度不能超过" + MAX_LENGTH + "位");
        }
        if (value.chars().anyMatch(Character::isWhitespace)) {
            throw new RuntimeException(label + "不能包含空格");
        }
        boolean hasLetter = value.chars().anyMatch(Character::isLetter);
        boolean hasDigit = value.chars().anyMatch(Character::isDigit);
        if (!hasLetter || !hasDigit) {
            throw new RuntimeException(label + "需同时包含字母和数字");
        }
    }
}
