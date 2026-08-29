package solvela.admin.auth;

/**
 * 令牌查询的三种结局。
 *
 * <p>用 sealed 接口而不是「返回 null 表示失败」，是因为<b>两种失败必须分得开</b>：
 * 前端对它们的处理完全不同 —— {@link Inactive} 弹「长时间未操作，请重新登录」，
 * {@link Unknown} 直接跳登录页。原先靠 sa-token 抛出的异常码（11016 vs 11011~11015）
 * 区分，也就是说这条产品行为依赖着一张框架内部的错误码表。
 */
public sealed interface TokenLookup {

    /** 令牌有效 */
    record Authenticated(AdminSession session) implements TokenLookup {
    }

    /**
     * 令牌本身没过期，但超过「最低活跃频率」没有操作过 —— 等保要求重新登录。
     */
    record Inactive() implements TokenLookup {
        static final Inactive INSTANCE = new Inactive();
    }

    /**
     * 没带、格式不对、已过期、已吊销 —— <b>一律不区分</b>。
     * 区分它们等于给攻击者一个「这个令牌存在过吗」的探测口。
     */
    record Unknown() implements TokenLookup {
        static final Unknown INSTANCE = new Unknown();
    }

    static TokenLookup inactive() {
        return Inactive.INSTANCE;
    }

    static TokenLookup unknown() {
        return Unknown.INSTANCE;
    }
}
