package com.ming.northstar_backend.support;

import com.ming.northstar_backend.entity.User;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 「QQ 与 Minecraft ID 各只允许绑定一次」的规则单元测试。
 *
 * <p>这条规则必须挡住的场景：玩家注册后用同一个账号把 QQ 或游戏 ID 改成别人的，
 * 从而把已经获批的内测资格转移出去。所以「已绑定后改不同的值」必须是硬错误，
 * 而「重复提交同一个值」「提交空值」不能误判成改动。</p>
 *
 * <p>另一个重点是<b>两个字段互相独立</b>：绑定 QQ 不能顺手把游戏 ID 也标成已绑定
 * （那会让玩家再也绑不上游戏 ID），管理员的强制改写也不能波及另一个字段。</p>
 */
class OnceBindingTest {

    private static final OnceBinding.Field QQ = OnceBinding.Field.QQ;
    private static final OnceBinding.Field MC_ID = OnceBinding.Field.MC_ID;

    private static final LocalDateTime YESTERDAY = LocalDateTime.now().minusDays(1);

    private static User user(String qq, String mcId) {
        User u = new User();
        u.setUsername("tester");
        u.setQq(qq);
        u.setQqBoundAt(qq == null || qq.isBlank() ? null : YESTERDAY);
        u.setMcId(mcId);
        u.setMcIdBoundAt(mcId == null || mcId.isBlank() ? null : YESTERDAY);
        return u;
    }

    @Test
    void firstBindingWritesValueAndTimestamp() {
        User u = user(null, null);

        assertFalse(OnceBinding.isBound(u, QQ));
        assertTrue(OnceBinding.bind(u, QQ, "13600000001"));

        assertEquals("13600000001", u.getQq());
        assertNotNull(u.getQqBoundAt(), "首次绑定必须记下绑定时间");
        assertTrue(OnceBinding.isBound(u, QQ));
    }

    @Test
    void firstBindingOfTheGameIdWorksTheSameWay() {
        User u = user(null, null);

        assertFalse(OnceBinding.isBound(u, MC_ID));
        assertTrue(OnceBinding.bind(u, MC_ID, "NovaSteve"));

        assertEquals("NovaSteve", u.getMcId());
        assertNotNull(u.getMcIdBoundAt(), "首次绑定必须记下绑定时间");
        assertTrue(OnceBinding.isBound(u, MC_ID));
    }

    @Test
    void rebindingToADifferentValueIsRejected() {
        User byQq = user("13600000001", null);
        RuntimeException qqError = assertThrows(RuntimeException.class,
                () -> OnceBinding.bind(byQq, QQ, "13600000002"));
        assertEquals(OnceBinding.QQ_MESSAGE, qqError.getMessage());
        assertEquals("13600000001", byQq.getQq(), "被拒绝时不能留下任何改动");

        User byMcId = user(null, "NovaSteve");
        RuntimeException mcError = assertThrows(RuntimeException.class,
                () -> OnceBinding.bind(byMcId, MC_ID, "NovaAlex"));
        assertEquals(OnceBinding.MC_ID_MESSAGE, mcError.getMessage());
        assertEquals("NovaSteve", byMcId.getMcId(), "被拒绝时不能留下任何改动");
    }

    @Test
    void resubmittingTheSameValueIsIdempotentAndKeepsTheOriginalTimestamp() {
        User u = user("13600000001", "NovaSteve");

        assertFalse(OnceBinding.bind(u, QQ, "13600000001"), "同一个值不算改动");
        assertFalse(OnceBinding.bind(u, MC_ID, "NovaSteve"), "同一个值不算改动");
        assertEquals(YESTERDAY, u.getQqBoundAt(), "重复提交不能刷新绑定时间");
        assertEquals(YESTERDAY, u.getMcIdBoundAt(), "重复提交不能刷新绑定时间");
    }

    @Test
    void blankInputNeitherBindsNorUnbinds() {
        User fresh = user(null, null);
        assertFalse(OnceBinding.bind(fresh, QQ, ""));
        assertFalse(OnceBinding.bind(fresh, QQ, null));
        assertFalse(OnceBinding.bind(fresh, MC_ID, ""));
        assertFalse(OnceBinding.bind(fresh, MC_ID, null));
        assertNull(fresh.getQq());
        assertNull(fresh.getMcId());
        assertNull(fresh.getQqBoundAt());
        assertNull(fresh.getMcIdBoundAt());

        User bound = user("13600000001", "NovaSteve");
        assertFalse(OnceBinding.bind(bound, QQ, ""), "空值不能把已绑定抹掉");
        assertFalse(OnceBinding.bind(bound, MC_ID, ""));
        assertEquals("13600000001", bound.getQq());
        assertEquals("NovaSteve", bound.getMcId());
    }

    @Test
    void bindingOneFieldDoesNotTouchTheOther() {
        User u = user(null, null);

        OnceBinding.bind(u, QQ, "13600000001");

        assertTrue(OnceBinding.isBound(u, QQ));
        assertFalse(OnceBinding.isBound(u, MC_ID), "绑定 QQ 不能把游戏 ID 也算成已绑定");
        assertNull(u.getMcId());
        assertNull(u.getMcIdBoundAt(), "另一个字段的绑定时间必须保持为空");

        // 仍然可以正常绑定游戏 ID
        assertTrue(OnceBinding.bind(u, MC_ID, "NovaSteve"));
        assertEquals("13600000001", u.getQq(), "绑定游戏 ID 不能动到 QQ");
    }

    @Test
    void adminForceSetCorrectsOrClearsTheBinding() {
        // 玩家把 QQ 填错了，管理员是唯一的纠正通道
        User u = user("13600000001", "NovaSteve");
        assertTrue(OnceBinding.forceSet(u, QQ, "13600000009"));
        assertEquals("13600000009", u.getQq());
        assertEquals(YESTERDAY, u.getQqBoundAt(), "纠错不算重新绑定，保留原始绑定时间");
        assertEquals("NovaSteve", u.getMcId(), "改 QQ 不能顺手动到游戏 ID");

        // 清空 = 解绑，之后玩家可以重新绑定一次
        assertTrue(OnceBinding.forceSet(u, QQ, ""));
        assertNull(u.getQq());
        assertNull(u.getQqBoundAt());

        assertTrue(OnceBinding.bind(u, QQ, "13600000010"), "解绑后应允许重新绑定一次");
        assertEquals("13600000010", u.getQq());

        // 值没变时不算改动，避免后台每次保存都刷一条日志
        assertFalse(OnceBinding.forceSet(u, QQ, "13600000010"));
        assertFalse(OnceBinding.forceSet(u, MC_ID, "NovaSteve"));
    }

    @Test
    void adminCanForceSetTheGameIdWithoutTouchingQq() {
        User u = user("13600000001", "NovaSteve");

        assertTrue(OnceBinding.forceSet(u, MC_ID, "NovaAlex"));

        assertEquals("NovaAlex", u.getMcId());
        assertEquals(YESTERDAY, u.getMcIdBoundAt());
        assertEquals("13600000001", u.getQq(), "改游戏 ID 不能顺手动到 QQ");
        assertEquals(YESTERDAY, u.getQqBoundAt());
    }
}
