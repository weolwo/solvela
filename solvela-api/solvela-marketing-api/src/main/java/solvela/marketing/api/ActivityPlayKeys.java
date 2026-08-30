package solvela.marketing.api;

/**
 * 玩法编排脚本执行时，绑在 {@code EngineContext} <b>内部数据通道</b>里的那几个 key。
 *
 * <h3>为什么这些值要走内部通道，而不是脚本变量</h3>
 * 脚本变量是<b>脚本可以重新赋值的</b>。会员号、幂等键这类东西一旦放在脚本变量里，
 * 一段这样的脚本就能把奖发给别人：
 * <pre>
 *   memberId = 10086;              // 改掉脚本变量
 *   return draw_draw('POOL_A');    // 函数如果读脚本变量，就替 10086 抽了
 * </pre>
 * 所以 Java 函数一律从<b>内部通道</b>取权威值 —— 那条通道脚本看不见，也就改不了。
 *
 * <p>脚本变量里<b>也</b>会绑一份 memberId / activityCode，那是给脚本做判断用的（读）。
 * 两份值在绑定时相同，之后脚本怎么改都影响不到函数拿到的那份。
 *
 * <h3>为什么定在契约模块</h3>
 * 绑定方在活动域（{@code ActivityFacade}），读取方在各玩法模块（如 marketing 的抽奖函数），
 * 两边都依赖本模块。key 写成字面量散在两处，改一个名就会漏掉一处 ——
 * 而漏掉的表现是函数拿到 null，不报错。
 */
public final class ActivityPlayKeys {

    /** 会员号。权威值，函数一律用它，不要读脚本变量里的同名值。 */
    public static final String MEMBER_ID = "__memberId";

    /** 活动编码。同上。 */
    public static final String ACTIVITY_CODE = "__activityCode";

    /**
     * 幂等键，由客户端一次点击生成一个。
     *
     * <p>透传给有副作用的函数去做去重 —— <b>幂等的语义归执行方</b>，
     * 因为只有它知道「重复」意味着什么（重复抽奖 vs 重复领奖，去重的粒度不同）。
     */
    public static final String REQUEST_ID = "__requestId";

    private ActivityPlayKeys() {
    }
}
