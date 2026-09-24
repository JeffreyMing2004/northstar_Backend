package com.ming.northstar_backend.support;

import com.ming.northstar_backend.entity.User;

import java.time.LocalDateTime;

/**
 * QQ 绑定规则：一个账号只允许绑定一次，绑定后玩家不可自行更改。
 *
 * <p>为什么要有这条规则：内测白名单以「QQ + 游戏ID」为键，QQ 就是内测资格的唯一凭据。
 * 如果允许随意改绑，玩家可以把已经获批的资格转手给别人，或者拿新 QQ 再领一份，
 * 白名单与账号之间的绑定关系也就形同虚设。</p>
 *
 * <p>规则集中在这一个类里，是为了让注册、资料修改、后台管理三条路径走同一份判定，
 * 避免某一处漏改就把限制绕过去了。</p>
 */
public final class QqBinding {

    /** 已绑定后再次提交不同 QQ 时的统一提示。 */
    public static final String ALREADY_BOUND_MESSAGE = "QQ 号已绑定，绑定后不可更改；如需更正请联系管理员";

    private QqBinding() {}

    /** 是否已经绑定过 QQ。 */
    public static boolean isBound(User user) {
        return user != null && user.getQq() != null && !user.getQq().isBlank();
    }

    /**
     * 首次绑定 QQ：仅在尚未绑定时写入，并记录绑定时间。
     *
     * @param user         目标用户
     * @param normalizedQq 已通过格式校验的 QQ；为空表示本次不提交该字段
     * @return 是否真的发生了写入
     * @throws RuntimeException 已绑定且本次提交的值与已绑定的不同
     */
    public static boolean bind(User user, String normalizedQq) {
        String next = normalizedQq == null ? "" : normalizedQq;
        if (next.isEmpty()) {
            return false;   // 未提交或留空：既不算绑定，也绝不解绑
        }
        if (isBound(user)) {
            if (user.getQq().equals(next)) {
                return false;   // 幂等：重复提交同一个 QQ 不算改动
            }
            throw new RuntimeException(ALREADY_BOUND_MESSAGE);
        }
        user.setQq(next);
        if (user.getQqBoundAt() == null) {
            user.setQqBoundAt(LocalDateTime.now());
        }
        return true;
    }

    /**
     * 管理员强制改写 QQ —— 玩家侧没有这条路径，仅用于运营纠正填错的号码。
     *
     * <p>传空串表示解绑：清空 QQ 与绑定时间，之后玩家可以重新绑定一次。
     * 这是唯一的重置手段，调用方应记录操作日志。</p>
     *
     * @return 是否真的发生了变化
     */
    public static boolean forceSet(User user, String normalizedQq) {
        String next = normalizedQq == null ? "" : normalizedQq;
        String current = user.getQq() == null ? "" : user.getQq();
        if (current.equals(next)) {
            return false;
        }
        if (next.isEmpty()) {
            user.setQq(null);
            user.setQqBoundAt(null);
            return true;
        }
        user.setQq(next);
        if (user.getQqBoundAt() == null) {
            user.setQqBoundAt(LocalDateTime.now());
        }
        return true;
    }
}
