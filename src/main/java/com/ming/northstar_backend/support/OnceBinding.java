package com.ming.northstar_backend.support;

import com.ming.northstar_backend.entity.User;

import java.time.LocalDateTime;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * 「只允许绑定一次」的账号字段规则：绑定后玩家不可自行更改，管理员不受限。
 *
 * <p><b>为什么 QQ 与 Minecraft ID 都要走这条规则</b>：内测白名单以「QQ + 游戏ID」为键，
 * 这两项就是内测资格的唯一凭据。如果允许随意改绑，玩家就能把已经获批的资格转手给别人，
 * 或者用一个新身份再领一份，白名单与账号之间的绑定关系也就形同虚设。</p>
 *
 * <p>规则集中在这一个类里、两个字段共用同一份判定，是为了让注册、资料修改、绑定游戏账号、
 * 后台管理这几条路径无法各写一套——否则只要有一处漏改，限制就被绕过去了。</p>
 *
 * <p>管理员侧走 {@link #forceSet}：玩家把 QQ 或游戏 ID 填错时，这是唯一的纠正通道，
 * 因此刻意保留，但要求调用方记录操作日志。</p>
 */
public final class OnceBinding {

    /** 已绑定 QQ 后再次提交不同值时的统一提示。 */
    public static final String QQ_MESSAGE = "QQ 号已绑定，绑定后不可更改；如需更正请联系管理员";

    /** 已绑定 Minecraft ID 后再次提交不同值时的统一提示。 */
    public static final String MC_ID_MESSAGE = "Minecraft ID 已绑定，绑定后不可更改；如需更正请联系管理员";

    /** 可一次性绑定的账号字段。 */
    public enum Field {

        QQ("QQ 号", QQ_MESSAGE,
                User::getQq, User::setQq, User::getQqBoundAt, User::setQqBoundAt),

        MC_ID("Minecraft ID", MC_ID_MESSAGE,
                User::getMcId, User::setMcId, User::getMcIdBoundAt, User::setMcIdBoundAt);

        private final String label;
        private final String alreadyBoundMessage;
        private final Function<User, String> reader;
        private final BiConsumer<User, String> writer;
        private final Function<User, LocalDateTime> boundAtReader;
        private final BiConsumer<User, LocalDateTime> boundAtWriter;

        Field(String label, String alreadyBoundMessage,
              Function<User, String> reader, BiConsumer<User, String> writer,
              Function<User, LocalDateTime> boundAtReader, BiConsumer<User, LocalDateTime> boundAtWriter) {
            this.label = label;
            this.alreadyBoundMessage = alreadyBoundMessage;
            this.reader = reader;
            this.writer = writer;
            this.boundAtReader = boundAtReader;
            this.boundAtWriter = boundAtWriter;
        }

        /** 字段中文名，用于日志与提示。 */
        public String label() {
            return label;
        }

        /** 已绑定后试图改绑时的提示原文（必须原样透给玩家）。 */
        public String alreadyBoundMessage() {
            return alreadyBoundMessage;
        }

        String read(User user) {
            return reader.apply(user);
        }

        void write(User user, String value) {
            writer.accept(user, value);
        }

        LocalDateTime readBoundAt(User user) {
            return boundAtReader.apply(user);
        }

        void writeBoundAt(User user, LocalDateTime value) {
            boundAtWriter.accept(user, value);
        }
    }

    private OnceBinding() {}

    /** 是否已经绑定过该字段。 */
    public static boolean isBound(User user, Field field) {
        if (user == null) {
            return false;
        }
        String current = field.read(user);
        return current != null && !current.isBlank();
    }

    /**
     * 首次绑定：仅在尚未绑定时写入，并记录绑定时间。
     *
     * @param user  目标用户
     * @param field 要绑定的字段
     * @param value 已通过格式校验的值；为空表示本次不提交该字段
     * @return 是否真的发生了写入
     * @throws RuntimeException 已绑定且本次提交的值与已绑定的不同
     */
    public static boolean bind(User user, Field field, String value) {
        String next = value == null ? "" : value.trim();
        if (next.isEmpty()) {
            // 未提交或留空：既不算绑定，也绝不解绑（避免前端误传空串把绑定抹掉）
            return false;
        }
        if (isBound(user, field)) {
            if (field.read(user).equals(next)) {
                return false;   // 幂等：重复提交同一个值不算改动
            }
            throw new RuntimeException(field.alreadyBoundMessage());
        }
        field.write(user, next);
        if (field.readBoundAt(user) == null) {
            field.writeBoundAt(user, LocalDateTime.now());
        }
        return true;
    }

    /**
     * 管理员强制改写 —— 玩家侧没有这条路径，仅用于运营纠正填错的值。
     *
     * <p>传空串表示解绑：清空该字段与绑定时间，之后玩家可以重新绑定一次。
     * 这是唯一的重置手段，调用方应记录操作日志。</p>
     *
     * @return 是否真的发生了变化
     */
    public static boolean forceSet(User user, Field field, String value) {
        String next = value == null ? "" : value.trim();
        String current = field.read(user) == null ? "" : field.read(user);
        if (current.equals(next)) {
            return false;
        }
        if (next.isEmpty()) {
            field.write(user, null);
            field.writeBoundAt(user, null);
            return true;
        }
        field.write(user, next);
        if (field.readBoundAt(user) == null) {
            field.writeBoundAt(user, LocalDateTime.now());
        }
        return true;
    }
}
