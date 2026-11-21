package solvela.marketing.api;

/**
 * 抽奖<b>没被受理</b>的原因。
 *
 * <h3>先分清三种结果，它们经常被混成一谈</h3>
 * <ul>
 *   <li><b>中奖</b>：{@code hit=true}；</li>
 *   <li><b>没中奖</b>：{@code hit=false} 且 {@code reject=null} —— 这是<b>正常结果</b>，
 *       抽奖本身发生了，只是没抽中；</li>
 *   <li><b>没被受理</b>：{@code reject!=null} —— 这一次<b>压根没抽</b>，机会没被消耗。
 *       ⚠️ 唯一例外是 {@link #DUPLICATE_REQUEST}，退还与否按 {@link #provesNotDrawn()} 判。</li>
 * </ul>
 * 前两者的区别决定要不要发奖，后两者的区别决定要不要退还机会/资产。
 * 混在一起表达（比如都用 message 字符串）时，这两个决定就只能靠猜。
 *
 * <h3>为什么定在契约里，而不是引擎自己一份</h3>
 * 引擎（{@code DrawExecuteService}）与契约共用这一个枚举，而不是各定一份再写映射表。
 * 映射表是会漂的：加一个原因忘了加映射，表现是「返回了个 null 原因」，编译不报错。
 * 共用的代价是引擎认识契约模块 —— 那个模块只有接口和 record，没有任何运行时负担。
 *
 * <h3>🔴 用返回值表达，不要抛异常</h3>
 * 这些全是<b>预期内</b>的情况（用户手快点了两下、奖池刚被运营关掉）。
 * 用异常表达它们，跨进程后一律变成 5xx —— 用户看到「服务开小差了」，
 * 监控上多一堆假的服务端错误，而真实原因只是「奖池关了」。
 */
public enum DrawRejectReason {

    /**
     * 同一个 requestId 已经在处理或处理过了。幂等拦截，<b>不是错误</b> —— 网络重试本来就该撞上它。
     *
     * <p>🔴 它是唯一<b>不表示「没抽」</b>的原因，见 {@link #provesNotDrawn()}。
     */
    DUPLICATE_REQUEST,

    /**
     * 请求的抽奖次数不合法（{@code times <= 0}）。
     *
     * <p>这不是业务规则而是<b>退化输入</b> —— 引擎刻意不校验次数上限（由上游保证），
     * 但 0 次和负数必须挡下：静默返回「抽了 0 次」会让上游以为成功了，
     * 而它多半是上游把次数算错了。
     */
    INVALID_TIMES,
    /** 触发了单用户单活动的防刷限流。 */
    TOO_FREQUENT,

    /**
     * 本周期的抽奖额度用完了 —— <b>由玩法编排脚本判定并拒绝</b>。
     *
     * <h3>🔴 它和 {@link #TOO_FREQUENT} 不是一回事</h3>
     * 那个是<b>防刷</b>：系统觉得你点得太快，等几秒就好。
     * 这个是<b>规则</b>：今天就这么多次，等到明天（或升一级）才有。
     * 合并成一句「稍后再试」会让用户一直重试一个永远不会成功的动作。
     *
     * <h3>为什么必须有这个值</h3>
     * 在它出现之前，一份算出「今天不该再抽了」的脚本<b>没有办法把结论说出去</b>：
     * 要么 {@code return null}（被 {@code validateOutput} 当成违约，
     * 用户看到一句点名脚本的开发者向报错），要么照抽 —— 额度形同虚设。
     */
    QUOTA_EXCEEDED,

    /**
     * 不符合参与条件（等级不够、不在目标人群里）—— <b>由玩法编排脚本判定并拒绝</b>。
     *
     * <p>⚠️ 措辞上刻意不说「等级不够」：这个值是给<b>所有</b>人群条件用的，
     * 而脚本能判的条件远不止等级。具体差在哪由脚本自己打日志，
     * 对用户统一说「不满足参与条件」——把判据抖给前端等于把风控规则也抖出去了。
     */
    NOT_ELIGIBLE,

    /** 奖池不存在，或不属于这个活动。 */
    POOL_NOT_FOUND,

    /** 奖池不是开启状态（未开始、已暂停、已结束）。 */
    POOL_CLOSED,

    /** 奖池一个奖项都没配。运营配置没做完，不是用户的问题。 */
    POOL_NO_PRIZE,

    /** 活动不存在。链接过期、编码被人乱改，都会走到这里。 */
    ACTIVITY_NOT_FOUND,

    /**
     * 活动此刻不可参与：未上线、还没开始，或已过<b>数据截止时间</b>。
     *
     * <p>注意判据是 {@code data_end_time} 而不是 {@code end_time} —— 数据截止之后活动还在，
     * 已中的奖还能领，只是不再受理新的参与。
     */
    ACTIVITY_NOT_OPEN,

    /**
     * 活动的玩法配置还没建（抽奖类活动没有对应的 {@code t_draw_config}）。
     *
     * <p>与 {@link #NO_PLAY_SCRIPT} 的区别是<b>缺的东西不一样</b>：
     * 这个是连玩法配置都没有，脚本写好了也没地方挂；那个是配置有了、脚本没挂上去。
     * 排查方向完全不同，所以不合并成一个。
     */
    NO_PLAY_CONFIG,

    /**
     * 活动没挂玩法编排脚本。运营配置没做完 —— 挂载点见
     * {@code ScriptRefPoint.DRAW_PLAY}（抽奖）或 {@code ACTIVITY_PLAY}（其它玩法）。
     */
    NO_PLAY_SCRIPT,

    /**
     * 奖池配置坏了：映射指向的奖项已被删除。
     *
     * <p>与 {@link #POOL_NO_PRIZE} 分开，是因为处置方式不同 ——
     * 前者是「还没配」，后者是「配过又被删坏了」，后者要告警。
     */
    POOL_BROKEN;

    /**
     * 这个原因能否证明这一批<b>压根没抽</b> —— 上游只有在它为 true 时才可以退还机会 / 资产。
     *
     * <p>🔴 {@link #DUPLICATE_REQUEST} 不能：撞上它时第一次<b>可能正在抽、也可能已经抽完</b>
     * （结果回放失败、结果没写进 Redis 时同样落到它）。据它退还，就是把已经中奖的那一批的成本退回去 ——
     * 连抽之后是 N 份奖的事。今天上游还没有消耗与退还，等加上时必须按这个方法判，而不是按「reject 非空」判。
     *
     * <p>其余原因都在开抽之前产生（入参、限流、奖池预检、活动与玩法配置、脚本拒绝），确实什么都没发生。
     *
     * <p>写成不带 default 的 switch：加一个原因时这里编译不过，逼着加的人回答「它能不能证明没抽」。
     * 这是方法不是字段，JSON 里仍是枚举名，跨进程形状不变。
     */
    public boolean provesNotDrawn() {
        return switch (this) {
            case DUPLICATE_REQUEST -> false;
            case INVALID_TIMES, TOO_FREQUENT, QUOTA_EXCEEDED, NOT_ELIGIBLE,
                 POOL_NOT_FOUND, POOL_CLOSED, POOL_NO_PRIZE, POOL_BROKEN,
                 ACTIVITY_NOT_FOUND, ACTIVITY_NOT_OPEN, NO_PLAY_CONFIG, NO_PLAY_SCRIPT -> true;
        };
    }
}
