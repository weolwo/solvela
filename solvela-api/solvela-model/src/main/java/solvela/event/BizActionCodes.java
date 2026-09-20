package solvela.event;

/**
 * 本仓<b>内置生产者</b>用到的业务动作编码。
 *
 * <h3>⚠️ 这不是事件注册表的副本</h3>
 * 「哪些事件是合法的、要不要带单号、怎么计量」的<b>唯一真源</b>是数据库里的
 * {@code t_task_event}（{@code TaskEventService} 的注释写着这一点）。
 * 本类只是把<b>平台自己那几个打点</b>的字符串收在一起，免得它们以字面量的形式
 * 散在 mall / member / external 三个模块里 —— 那样改一个名字要靠全文搜索。
 *
 * <p>🔴 <b>别把注册表里的每一行都往这里抄。</b>外部系统上报的事件、运营在后台
 * 新增的事件，都不该在这里出现 —— 它们没有对应的 Java 生产者。
 * 抄进来的那一刻，这个类就变成了一份会过期的副本，而且没人会记得更新它。
 *
 * <p>加一个<b>新的内部打点</b>才在这里加一行；加一个<b>新的事件</b>只需要
 * 在后台的事件注册表里加一行数据（{@code GOODS_SHARE} 那行种子数据的 remark
 * 写着「用来演示加事件只加一行数据、前端零改动」）。
 *
 * <h3>这些编码不是"任务术语"</h3>
 * {@code ORDER_PAID} 描述的是<b>商城自己发生的事</b>，任务注册表只是恰好用同一个字符串
 * 订阅它。判据：把 {@code solvela-marketing} 摘掉，{@code ORDER_PAID} 在商城里依然有意义。
 *
 * @author alaric
 * @date 2026-09-17
 */
public final class BizActionCodes {

    private BizActionCodes() {
    }

    /**
     * 会员注册成功。幂等键用会员号 —— 一个会员一辈子只会有一次。
     *
     * <p>生产者：{@code MemberRegisterService.createMember}
     */
    public static final String MEMBER_REGISTER = "MEMBER_REGISTER";

    /**
     * 商城订单支付完成（计次）。
     *
     * <p>🔴 <b>生产者有两个，不是一个</b>：混合单走 {@code MallPayService.pay}，
     * 纯积分单走 {@code MallRedeemService.redeem}（落单那一刻资产就结清，不经过支付）。
     * 只埋前者的表现是「纯积分兑换不算任务进度」，而纯积分单恰恰是主要的兑换方式 ——
     * 它不报错、不打日志，只会变成客诉。
     */
    public static final String ORDER_PAID = "ORDER_PAID";

    /**
     * 商城订单金额累计（计额）。与 {@link #ORDER_PAID} <b>同一时刻、同一批产生</b>，
     * 分成两个编码是因为 {@code t_task_event.metric_source} 不同：
     * 一个计次（NONE），一个按 payload 里的 {@code payAmount} 计额。
     *
     * <p>「累计消费满 500 元」这类任务订阅的是这一个。
     */
    public static final String ORDER_AMOUNT = "ORDER_AMOUNT";

    /**
     * 每日签到。天然<b>没有</b>业务单号，由服务端按事件自然日兜底幂等（一天算一次）。
     *
     * <p>生产者：{@code MemberSignService.sign}
     */
    public static final String DAILY_SIGN = "DAILY_SIGN";

    /**
     * 外部场景消费成功（充话费）。幂等键用外部消费单号。
     *
     * <p>生产者：{@code ExternalRechargeService.payAndExecute}
     *
     * <p>⚠️ 这一条<b>不在</b> {@code data-baseline.sql} 的种子数据里，
     * 由 {@code 任务打点-事件注册.sql} 补。没执行那个脚本时，
     * 打点会被防腐层<b>安静地忽略</b>（注册表查不到就返回），不会报错也不会刷日志。
     */
    public static final String RECHARGE_PAID = "RECHARGE_PAID";

    /**
     * 积分入账（会员的 SCORE 钱包收到了一笔）。幂等键用流水的 {@code biz_ref_id}。
     *
     * <p>生产者：{@code ScoreIncomeActionPublisher}（资产域），
     * 消费者：会员域的成长值累加器。
     *
     * <h3>🔴 它广播的是【全部】SCORE 入账，包括不该算成长值的那些</h3>
     * 「哪些算」是<b>会员域</b>的判断（按 payload 里的 {@code bizType} 白名单），
     * 不是资产域的。资产域该说的只有「入账了，来源是 X」——
     * 让它知道「等级」这个概念存在，就等于把会员域的规则漏进了账本。
     *
     * <p>⚠️ 所以<b>不要</b>在发布处按 bizType 过滤。那看起来省事，
     * 实际是把一个会变的业务规则焊死在了最不该变的那一层。
     */
    public static final String SCORE_EARNED = "SCORE_EARNED";
}
