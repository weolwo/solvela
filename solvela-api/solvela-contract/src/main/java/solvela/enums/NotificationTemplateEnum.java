package solvela.enums;

import lombok.Getter;

import java.util.List;

/**
 * 通知模板编码，对齐 {@code t_notification_template.template_code}。
 *
 * <h3>为什么是枚举而不是纯字符串常量</h3>
 * 模板内容是数据（运营可改、按版本存库），但<b>「有哪些模板」是代码契约</b> ——
 * 业务方调 {@code NotificationService.send()} 时传的就是这里的值。写成枚举，
 * 拼错的模板编码在编译期就挂了，而不是等到运行时查不到模板、通知静默不发。
 *
 * <h3>{@link #requiredParams} 是给发送方看的清单</h3>
 * 库里 {@code t_notification_template.param_keys} 也存了一份，两边**故意重复**：
 * <ul>
 *   <li>枚举这份是<b>编译期文档</b>，写调用代码的人不用去翻库就知道该传什么；</li>
 *   <li>库里那份是<b>运行期校验</b>，且跟着 version 走 —— 模板改版加了新占位符，
 *       校验能立刻发现老调用方没传。</li>
 * </ul>
 *
 * <p>🔴 两边不一致时<b>以库里的为准</b>，因为模板是版本化的而枚举不是。
 * 枚举这份漂了只会让人多传一个没用的参数（无害），反过来漏传才会渲染出
 * 字面的 {@code ${xxx}} 给用户看 —— 那是 {@code SolvelaTemplateUtil}
 * 「解析不到原样保留」的口径决定的，见方案 §4.2。
 *
 * @Author alaric
 * @Date 2026-09-14
 */
@Getter
public enum NotificationTemplateEnum {

    /**
     * 人工发送：客服 / 运营在后台手动发给某个（或某几个）会员的一条站内信。
     *
     * <h3>它是唯一一个「正文由发送方现填」的模板</h3>
     * 占位符就是 {@code ${title}} 和 {@code ${content}}，也就是说真正的文字
     * 落在 {@code params} 里，而不是模板里。
     *
     * <p>这看着像是绕开了模板化，其实不是 —— <b>模板化要省的是「同一段文字存 N 遍」</b>。
     * 人工发送一次只面向个位数到几百个会员，那段文字本来就没有重复可消除。
     * 为它单开一张带 content 列的表，或者给 {@code t_member_notification} 加一个
     * 常年为空的 content 列，都是更差的选择：多一套存储模型，而省不下任何东西。
     *
     * <h3>🔴 归在 SYSTEM，意味着用户关不掉</h3>
     * 人工触达通常是「针对你这个人的事」（工单答复、账号说明、补偿通知），
     * 不该被免打扰静音。
     *
     * <p>⚠️ 代价是它成了一条绕过免打扰的路：运营拿它群发营销文案，用户关了
     * 也照样收到。挡这件事靠的不是技术而是<b>留痕 + 条数上限</b> ——
     * {@code create_by} 记着是谁发的，且单次收件人有硬上限
     * （见 {@code NotificationAdminService.MANUAL_MAX_RECIPIENTS}）。
     * 真要发给所有人，那是公告该干的事。
     */
    MANUAL("MANUAL", NotificationCategoryEnum.SYSTEM,
            List.of("title", "content")),

    /**
     * 中奖：抽奖 / 任务 / 彩票三条链路共用。
     *
     * <p>发送点是 {@code LocalPrizeDispatchResultPublisher} —— 资产<b>真的到账</b>那一刻，
     * 不是「中奖」那一刻。两者差着一整条提案链路：中了奖还可能卡人工审批、
     * 还可能因为预算耗尽失败。在受理时就发「恭喜中奖」，然后发不出去，比不发更糟。
     *
     * <p>⚠️ <b>参数里没有活动名</b>，只有奖品名和数量。同样是拿不到：
     * 回写点在 {@code solvela-ledger}，而活动表在 {@code solvela-marketing} ——
     * marketing 排在 ledger <b>之后</b>，ledger 依赖它会直接成环。
     * {@code t_prize_log} 上只有 {@code activity_code}（形如 ACT_618），
     * 那个码对用户毫无意义，不如不显示。
     */
    PRIZE_WON("PRIZE_WON", NotificationCategoryEnum.MARKETING,
            List.of("prizeName", "amount")),

    /**
     * 发货：实物履约单回填物流单号时发。
     *
     * <p>⚠️ <b>参数里没有商品名</b>，只有来源单号。不是漏了，是拿不到：
     * 发货这件事发生在 {@code solvela-ledger} 的履约域，而 {@code t_physical_delivery}
     * 上只有 {@code source_biz_id}（订单号 / 提案号），没有商品名快照；
     * 想去查商品得让 ledger 依赖 mall，而那条缝是<b>单向</b>的
     * （mall → ledger），由 {@code MallLedgerBoundaryTest} 守着。
     *
     * <p>真要在通知里显示商品名，正解是给 {@code t_physical_delivery}
     * 加一个商品名<b>快照列</b>（和 {@code t_mall_order.commodity_name} 同一个套路，
     * 由 mall 建履约单时写进来），而不是反向打通依赖。那是另一个 PR 的事。
     */
    DELIVERY_SHIPPED("DELIVERY_SHIPPED", NotificationCategoryEnum.TRADE,
            List.of("sourceBizId", "logisticsCompany", "logisticsNo")),

    /**
     * 商城订单履约失败：东西没发出去，但<b>积分不退</b>。
     *
     * <p>🔴 文案里千万别写「积分已退回」。{@code MallOrderDao.markFailed} 的注释把这条
     * 定死了：<b>失败不退积分 —— 东西还欠着用户，不是没买。</b>
     * 退了等于单方面取消订单，而运营可能只是漏配了券模，补上就能发。
     * 真正的取消是另一条路（40-已取消 + refund），对应 {@link #ORDER_CANCELLED}。
     *
     * <p>两条路的文案必须泾渭分明，否则用户会按「积分回来了」去理解一次
     * 「我们欠着你」，然后再兑一单。
     */
    ORDER_FULFILL_FAILED("ORDER_FULFILL_FAILED", NotificationCategoryEnum.TRADE,
            List.of("orderNo", "commodityName", "failReason")),

    /**
     * 商城订单超时取消：<b>积分已原路退回</b>。
     *
     * <p>与 {@link #ORDER_FULFILL_FAILED} 是<b>两件事</b>，别合并成一个模板：
     * 前者是「东西还欠着你」，后者是「这单不算数了，钱还你」。
     *
     * <p>发送点是 {@code MallOrderExpireJob} —— 积分是静悄悄退回去的，
     * 不说一声的话，用户只会看到订单莫名消失、积分数字莫名变了。
     */
    ORDER_CANCELLED("ORDER_CANCELLED", NotificationCategoryEnum.TRADE,
            List.of("orderNo", "commodityName", "refundPoints")),

    /**
     * 账号被限制（冻结 / 锁定）。
     *
     * <p>归在 {@link NotificationCategoryEnum#SYSTEM} 是刻意的：这条不允许被免打扰关掉。
     */
    ACCOUNT_LIMITED("ACCOUNT_LIMITED", NotificationCategoryEnum.SYSTEM,
            List.of("limitType", "unlockTime")),

    /**
     * 优惠券即将过期。
     *
     * <p>🔴 <b>必须合并发送</b>：一个人有 8 张券要过期，发<b>一条</b>
     * 「您有 8 张券即将过期」，不是 8 条。所以参数是 {@code count} 而不是券名 ——
     * 参数形状本身就把「一券一条」这种写法挡掉了。见方案 §8。
     */
    COUPON_EXPIRING("COUPON_EXPIRING", NotificationCategoryEnum.TRADE,
            List.of("count", "nearestExpireTime")),
    ;

    /**
     * 模板编码。与枚举名一致，但<b>单独留一个字段</b>：
     * 库里存的是这个字符串，改枚举名时不该影响存量数据。
     */
    private final String code;

    /** 默认分类。库里模板行上也有一份，以库里的为准（运营可以调） */
    private final NotificationCategoryEnum category;

    /** 本模板期望的占位符，见类注释 */
    private final List<String> requiredParams;

    NotificationTemplateEnum(String code, NotificationCategoryEnum category, List<String> requiredParams) {
        this.code = code;
        this.category = category;
        this.requiredParams = requiredParams;
    }
}
