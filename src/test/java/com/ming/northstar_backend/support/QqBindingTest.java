package com.ming.northstar_backend.support;

import com.ming.northstar_backend.entity.User;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * QQ 一次性绑定规则的单元测试。
 *
 * <p>这条规则必须挡住的场景：玩家注册后用同一个账号把 QQ 改成别人的号码，
 * 从而把已经获批的内测资格转移出去。所以「已绑定后改不同的值」必须是硬错误，
 * 而「重复提交同一个值」「提交空值」则不能被误判成改动。</p>
 */
class QqBindingTest {

    private static final LocalDateTime YESTERDAY = LocalDateTime.now().minusDays(1);

    private static User user(String qq) {
        User u = new User();
        u.setUsername("tester");
        u.setQq(qq);
        u.setQqBoundAt(qq == null || qq.isBlank() ? null : YESTERDAY);
        return u;
    }

    @Test
    void firstBindingWritesValueAndTimestamp() {
        User u = user(null);

        assertFalse(QqBinding.isBound(u));
        assertTrue(QqBinding.bind(u, "13600000001"));

        assertEquals("13600000001", u.getQq());
        assertNotNull(u.getQqBoundAt(), "首次绑定必须记下绑定时间");
        assertTrue(QqBinding.isBound(u));
    }

    @Test
    void rebindingToADifferentQqIsRejected() {
        User u = user("13600000001");

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> QqBinding.bind(u, "13600000002"));

        assertEquals(QqBinding.ALREADY_BOUND_MESSAGE, ex.getMessage());
        assertEquals("13600000001", u.getQq(), "被拒绝时不能留下任何改动");
        assertEquals(YESTERDAY, u.getQqBoundAt());
    }

    @Test
    void resubmittingTheSameQqIsIdempotentAndKeepsTheOriginalTimestamp() {
        User u = user("13600000001");

        assertFalse(QqBinding.bind(u, "13600000001"), "同一个值不算改动");
        assertEquals(YESTERDAY, u.getQqBoundAt(), "重复提交不能刷新绑定时间");
    }

    @Test
    void blankInputNeitherBindsNorUnbinds() {
        User fresh = user(null);
        assertFalse(QqBinding.bind(fresh, ""));
        assertFalse(QqBinding.bind(fresh, null));
        assertNull(fresh.getQq());
        assertNull(fresh.getQqBoundAt());

        User bound = user("13600000001");
        assertFalse(QqBinding.bind(bound, ""), "空值不能把已绑定抹掉");
        assertFalse(QqBinding.bind(bound, null));
        assertEquals("13600000001", bound.getQq());
        assertEquals(YESTERDAY, bound.getQqBoundAt());
    }

    @Test
    void adminForceSetCorrectsOrClearsTheBinding() {
        // 玩家把 QQ 填错了，管理员是唯一的纠正通道
        User u = user("13600000001");
        assertTrue(QqBinding.forceSet(u, "13600000009"));
        assertEquals("13600000009", u.getQq());
        assertEquals(YESTERDAY, u.getQqBoundAt(), "纠错不算重新绑定，保留原始绑定时间");

        // 清空 = 解绑，之后玩家可以重新绑定一次
        assertTrue(QqBinding.forceSet(u, ""));
        assertNull(u.getQq());
        assertNull(u.getQqBoundAt());

        assertTrue(QqBinding.bind(u, "13600000010"), "解绑后应允许重新绑定一次");
        assertEquals("13600000010", u.getQq());

        // 值没变时不算改动，避免后台每次保存都刷一条日志
        assertFalse(QqBinding.forceSet(u, "13600000010"));
    }
}
