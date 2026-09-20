package solvela.mall.order.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import solvela.enums.MallCommodityStatusEnum;
import solvela.enums.MallOrderStatusEnum;
import solvela.enums.MallPayTypeEnum;
import solvela.mall.MallAddress;
import solvela.mall.MallCommodity;
import solvela.mall.MallOrder;
import solvela.mall.MallSku;
import solvela.mall.address.service.MallAddressService;
import solvela.mall.commodity.manager.MallCommodityManager;
import solvela.mall.exchangelimit.dao.MallExchangeLimitDao;
import solvela.mall.order.event.MallOrderActionPublisher;
import solvela.mall.order.event.MallOrderPendingEvent;
import solvela.mall.order.manager.MallOrderManager;
import solvela.mall.sku.dao.MallSkuDao;
import solvela.mall.sku.manager.MallSkuManager;
import solvela.marketing.api.MallRedeemCmd;
import solvela.marketing.api.MallRedeemReason;
import solvela.marketing.api.MallRedeemResult;
import solvela.member.api.AssetDebitApi;
import solvela.member.api.AssetDebitCmd;
import solvela.member.api.CouponLockCmd;
import solvela.member.api.CouponQueryApi;
import solvela.member.api.CouponTrialQuery;
import solvela.member.api.CouponTrialView;
import solvela.member.api.CouponWriteOffApi;
import solvela.member.api.CouponWriteOffCmd;
import solvela.member.api.CouponWriteOffView;
import solvela.member.api.AssetDebitResult;
import solvela.member.service.MemberService;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 兑换下单。<b>整条链路只有这一个入口。</b>
 *
 * <h3>事务边界就是这个方法</h3>
 * 锁库存 → 扣限兑 → 扣积分 → 落订单，<b>任何一步失败整体回滚</b>。
 * 扣积分走 {@link AssetDebitApi}，今天在同进程里解析成 ledger 的 bean，
 * 所以事务能穿透；拆成独立服务之后这里要改成 saga 或本地消息表 ——
 * 那一天到来时，这段注释就是要回来看的地方。
 *
 * <h3>🔴 顺序不能换</h3>
 * <b>先占资源（库存、限兑），最后扣钱。</b>反过来的话，扣完积分才发现没库存，
 * 虽然事务会回滚，但那次扣减已经在 {@code t_member_asset_transaction} 里
 * 占掉了 {@code uk(biz_ref_id, asset_type)} 这个唯一键 —— 回滚能撤销插入，
 * 可万一事务边界被谁改坏了（比如有人给某一步加了 REQUIRES_NEW），
 * 表现就是「钱扣了、单没有、重试还说重复提交」。顺序本身就是一道防线。
 *
 * <h3>🔴 order_no 是服务端生成的，同时是扣积分的幂等键</h3>
 * DDL 明写：把 biz_ref_id 传 order_no，<b>重复扣款天然幂等，不要另造去重表</b>。
 * 客户端那个 requestId 是另一件事（挡连点），挡在网关。
 *
 * <h3>履约不在这里</h3>
 * 状态机是 {@code 0 →(支付/直接扣分)→ 10 →(投递履约)→ 20 →(履约回执)→ 30/60}。
 * 本方法到 <b>10-待履约</b>（或 payType=2 时的 0-待支付）为止，
 * 投递履约是独立一步 —— 把它塞进这个事务，等于让一次外部调用
 *（写发货单 / 发券）决定用户的积分扣不扣得成。
 *
 * <p>它在 {@link MallFulfillService}，由本方法发出的
 * {@link MallOrderPendingEvent} 在<b>提交之后</b>触发。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MallRedeemService {

    /** 订单号前缀。一眼看出是商城单，客服不用去查表 */
    private static final String ORDER_NO_PREFIX = "M";

    private static final DateTimeFormatter ORDER_NO_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");

    /** 待支付超时。只有 payType=2 会用到 */
    private static final int PAY_EXPIRE_MINUTES = 30;

    private static final String BIZ_TYPE = "MALL_EXCHANGE";

    /**
     * 券核销流水里的业务类型。
     *
     * <p>⚠️ 和 {@link #BIZ_TYPE} 刻意不同：那个是<b>资产流水</b>的归因
     *（{@code t_member_asset_transaction}），这个是<b>券核销流水</b>的
     *（{@code t_coupon_write_off}）。两张表各自的取值域不该被绑在一起 ——
     * 哪天资产流水要细分兑换类型，券那边不该跟着变。
     */
    private static final String BIZ_TYPE_COUPON = "MALL";

    /** 一次最多兑几件。不封的话一个 quantity=99999 会把库存条件判断变成一次巨额扣减 */
    private static final int MAX_QUANTITY = 20;

    private static final SecureRandom RANDOM = new SecureRandom();

    private final MallCommodityManager mallCommodityManager;
    private final MallSkuManager mallSkuManager;
    private final MallSkuDao mallSkuDao;
    private final MallExchangeLimitDao mallExchangeLimitDao;
    private final MallOrderManager mallOrderManager;
    private final MallAddressService mallAddressService;
    private final MemberService memberService;
    private final AssetDebitApi assetDebitApi;
    private final CouponQueryApi couponQueryApi;
    private final CouponWriteOffApi couponWriteOffApi;
    private final ApplicationEventPublisher eventPublisher;
    /**
     * 「这一单付掉了」的广播。纯积分单在<b>落单那一刻</b>就已经结清，
     * 所以它的付款动作产生在这里，而不是在 {@code MallPayService} ——
     * 那是 ORDER_PAID 的两个产生点里最容易被漏掉的一个。
     */
    private final MallOrderActionPublisher orderActionPublisher;

    /**
     * 兑换。
     *
     * <h3>🔴 读这段代码只需要认准一件事：{@code ofReject} 还是 {@code reject}</h3>
     * 分水岭是「有没有开始写库」：
     * <pre>
     *   校验阶段失败 -> MallRedeemResult.ofReject(...)   什么都没写，普通返回
     *   占了资源之后 -> reject(...)                      内部会 markRollbackOnly()
     * </pre>
     * 在中间插一条新的拒绝分支却用了 {@code ofReject}，后果是<b>库存扣了、限兑占了、
     * 订单没落，用户什么都没拿到</b> —— 不报错、不打日志，只有库里那件商品少了一件。
     * {@code MallRedeemServiceTest} 把每一条拒绝路径的回滚与否都钉住了。
     */
    @Transactional(rollbackFor = Exception.class)
    public MallRedeemResult redeem(MallRedeemCmd cmd) {
        int quantity = clampQuantity(cmd.quantity());

        MallSku sku = mallSkuManager.getById(cmd.skuId());
        if (sku == null) {
            return MallRedeemResult.ofReject(MallRedeemReason.SKU_NOT_FOUND);
        }
        MallCommodity commodity = mallCommodityManager.getById(sku.getCommodityId());
        if (commodity == null || !isVisible(commodity)) {
            // 已下架与不存在给同一个原因：对用户都是「兑不了」
            return MallRedeemResult.ofReject(MallRedeemReason.COMMODITY_OFF);
        }

        // ---------- 以下是校验阶段：还没写任何东西，拒绝一律用 ofReject ----------
        AddressResolution addr = resolveAddress(cmd, commodity);
        if (addr.problem() != null) {
            return MallRedeemResult.ofReject(addr.problem());
        }
        MallAddress address = addr.address();

        boolean hangs = MallPayTypeEnum.POINTS_CASH == commodity.getPayType();
        if (!reserveStock(sku, quantity, hangs)) {
            // ① 本身失败 = 条件 UPDATE 影响 0 行 = 没扣成，没有任何东西需要撤销
            return MallRedeemResult.ofReject(MallRedeemReason.OUT_OF_STOCK);
        }

        // ---------- 从这里开始已经占了资源：每一条拒绝都必须走 reject ----------
        if (!consumeExchangeLimit(cmd.memberId(), commodity, quantity)) {
            return reject(MallRedeemReason.EXCHANGE_LIMITED);
        }

        // 订单号先生成：它同时是扣积分与锁券的幂等键，必须在扣款之前就定下来
        String orderNo = generateOrderNo();
        int originalPoints = resolvePoints(sku, commodity) * quantity;

        /*
         * ④ 用券。放在占限购之后、扣款之前 ——
         *    它和库存、限购是同一类「已占资源」，失败要一起回滚。
         */
        CouponUse couponUse = applyCoupon(cmd, commodity, sku, orderNo, originalPoints, quantity, hangs);
        if (couponUse.problem() != null) {
            return reject(couponUse.problem());
        }
        int payPoints = originalPoints - couponUse.discountPoints();

        MallRedeemReason debitProblem = debitPoints(cmd.memberId(), commodity, orderNo, payPoints);
        if (debitProblem != null) {
            return reject(debitProblem);
        }

        MallOrder order = buildOrder(cmd, commodity, sku, quantity, orderNo, payPoints, hangs, address);
        order.setCouponId(couponUse.couponId());
        order.setCouponDiscount(couponUse.couponId() == null ? null : couponUse.discount());
        if (couponUse.deductsCash()) {
            // 抵现金：实付现金 = 原本的应付现金 - 抵扣。不能减成负数
            order.setPayCash(order.getPayCash().subtract(couponUse.discountCash()).max(BigDecimal.ZERO));
        }
        mallOrderManager.save(order);

        confirmCouponIfSettled(order, couponUse);
        publishFulfillment(order);

        return MallRedeemResult.ofAccepted(orderNo, order.getStatus());
    }

    /**
     * 用券的结果。{@code problem != null} 即被拒。
     *
     * @param couponId    锁上的券；没用券时为 null
     * @param discount    实际抵掉多少。<b>单位由 {@code deductsCash} 决定</b>：元 或 积分
     * @param deductsCash 这张券抵的是现金那一半还是积分那一半
     * @param problem     被拒的原因；{@code null} = 没被拒
     */
    private record CouponUse(Long couponId, BigDecimal discount, boolean deductsCash,
                             MallRedeemReason problem) {

        static final CouponUse NONE = new CouponUse(null, BigDecimal.ZERO, false, null);

        static CouponUse rejected(MallRedeemReason problem) {
            return new CouponUse(null, BigDecimal.ZERO, false, problem);
        }

        /**
         * 抵掉的积分。抵现金的券在这里是 0 —— 积分那一部分没被动过。
         *
         * <p>🔴 靠的是<b>券自己的</b> deduct_target，不是订单的付款方式。
         * 2026-09-15 之前这里看的是「是不是混合单」，于是混合单上的积分券
         * 会被错误地当成现金券去减现金。
         */
        int discountPoints() {
            return deductsCash ? 0 : discount.intValue();
        }

        /** 抵掉的现金。抵积分的券在这里是 0 */
        BigDecimal discountCash() {
            return deductsCash ? discount : BigDecimal.ZERO;
        }
    }

    /**
     * ④ 用券：试算 → 锁定。
     *
     * <h3>🔴 抵扣额由<b>服务端重新试算</b>，不信客户端传的数</h3>
     * 客户端只说「用哪张券」。让它传抵扣额，「减多少」就成了客户端说了算 ——
     * 那是一个可以直接刷钱的口子，而且不会有任何报错。
     *
     * <h3>🔴 抵积分还是抵现金，由<b>券自己的</b> {@code deduct_target} 决定</h3>
     * 2026-09-15 改。阶段 6 曾经定了条「混合单只抵现金」的规则，把 {@code SCORE} 券
     * 挡在了混合单外面，理由是「要让两种券都能用就得先回答先抵哪一部分」。
     *
     * <p><b>那个理由是错的</b>：一单一券的前提下只有一张券，它的 {@code deduct_target}
     * 就唯一决定了抵哪一半，根本不存在「先抵哪部分」这个问题 ——
     * 那是把<b>叠加</b>场景的顾虑套到了非叠加场景上。
     *
     * <p>现在两个应付都传给试算，积分券减 {@code payPoints}、现金券减 {@code payCash}。
     * 不可比的是<b>推荐</b>（10 积分和 5 块钱谁更划算答不了），不是可用性。
     *
     * <p>阶段 4 曾经<b>完全禁止</b> {@code POINTS_CASH} 用券，因为那时没有任何地方
     * 能把券从「锁定中」推到「已使用」—— 券会被兜底任务放回去，这一单白给折扣。
     * 阶段 6 的假支付（{@code MallPayService}）补上了那个确认点，所以这里放开了。
     */
    private CouponUse applyCoupon(MallRedeemCmd cmd, MallCommodity commodity, MallSku sku,
                                  String orderNo, int originalPoints, int quantity, boolean hangs) {
        if (cmd.couponId() == null) {
            return CouponUse.NONE;
        }

        BigDecimal payPoints = BigDecimal.valueOf(originalPoints);
        // 只有混合支付单才真的要付现金；纯积分单的 cash_price 即使配了也不收
        BigDecimal payCash = hangs
                ? resolveCash(sku, commodity).multiply(BigDecimal.valueOf(quantity))
                : BigDecimal.ZERO;
        if (payPoints.signum() <= 0 && payCash.signum() <= 0) {
            // 0 元 0 分的商品用券没有意义，而且会算出一张「减 0」的核销流水
            return CouponUse.rejected(MallRedeemReason.COUPON_UNUSABLE);
        }

        CouponTrialView trial = couponQueryApi.trial(new CouponTrialQuery(
                cmd.memberId(), payPoints, payCash,
                commodity.getCommodityCode(), String.valueOf(commodity.getCategoryId()), null));

        // 只认用户点的那一张：试算的推荐是给页面看的，下单要用的是用户实际选的
        CouponTrialView.Item chosen = trial.allUsable().stream()
                .filter(item -> cmd.couponId().equals(item.couponId()))
                .findFirst()
                .orElse(null);
        if (chosen == null) {
            log.info("【商城用券】券 {} 不在可用列表里，会员 {} 订单 {}",
                    cmd.couponId(), cmd.memberId(), orderNo);
            return CouponUse.rejected(MallRedeemReason.COUPON_UNUSABLE);
        }

        BigDecimal discount = chosen.discountAmount();
        if (discount == null || discount.signum() <= 0) {
            // 算出来减 0，用券就没有意义 —— 让它落单反而会在券包里留下一张「已使用」的空账
            return CouponUse.rejected(MallRedeemReason.COUPON_UNUSABLE);
        }

        /*
         * 流水里的「抵扣前金额」要记【这一侧的】应付：现金券记 payCash，积分券记 payPoints。
         * 记错了对账时看到的是「在 5000 积分上减了 10 元」这种读不懂的行。
         */
        boolean deductsCash = "CASH".equals(chosen.deductTarget());
        BigDecimal payAmount = deductsCash ? payCash : payPoints;

        CouponWriteOffView locked = couponWriteOffApi.lock(new CouponLockCmd(
                cmd.couponId(), cmd.memberId(), BIZ_TYPE_COUPON, orderNo, null,
                payAmount, discount));
        if (!locked.ok()) {
            // 试算到锁定之间的窗口里被另一笔单抢走了。对用户就是「这张券用不了，换一张」
            log.info("【商城用券】券 {} 锁不上：{}，订单 {}", cmd.couponId(), locked.message(), orderNo);
            return CouponUse.rejected(MallRedeemReason.COUPON_UNUSABLE);
        }
        return new CouponUse(cmd.couponId(), discount, deductsCash, null);
    }

    /**
     * 订单一旦落成<b>待履约</b>，积分就已经扣掉且没有回头路了 —— 这时候确认券。
     *
     * <h3>🔴 为什么不是等履约完成再确认</h3>
     * 履约失败那条路<b>刻意不退积分</b>（「东西还欠着用户，不是没买」，
     * 见 {@code MallOrderDao.markFailed}）。既然积分不退，券也不该退 ——
     * 否则用户会拿到一个自相矛盾的结果：券回来了，积分没回来。
     *
     * <p>而如果在那之前一直挂着「锁定中」，兜底任务会在 120 分钟后把它放回去，
     * 于是变成「积分扣了、折扣享了、券还在」。两种都是漏钱，方向相反。
     *
     * <p>所以确认点是<b>资产结清的那一刻</b>，不是履约成功的那一刻。
     *
     * <h3>⚠️ 确认失败只能告警，不能回滚</h3>
     * 到这一步积分已经扣了、订单已经落了。为了一张券把整单回滚，是拿一次
     * 确定的成功去换一次确定的失败。所以这里打 ERROR 等人工核对 ——
     * 而且这条几乎不可能发生：券是本事务几行之前刚锁上的。
     */
    private void confirmCouponIfSettled(MallOrder order, CouponUse couponUse) {
        if (couponUse.couponId() == null || MallOrderStatusEnum.PENDING != order.getStatus()) {
            return;
        }
        CouponWriteOffView confirmed = couponWriteOffApi.confirm(new CouponWriteOffCmd(
                couponUse.couponId(), BIZ_TYPE_COUPON, order.getOrderNo(), null));
        if (!confirmed.ok()) {
            log.error("【商城用券】🔴 券 {} 确认失败：{}。订单 {} 已按抵扣后金额扣了积分，"
                            + "但券没被标成已使用 —— 用户可能把它再用一次，请人工核对",
                    couponUse.couponId(), confirmed.message(), order.getOrderNo());
        }
    }

    /** 收货地址的解析结果。{@code problem != null} 即被拒，两者必有其一为 null */
    private record AddressResolution(MallAddress address, MallRedeemReason problem) {

        static AddressResolution notNeeded() {
            return new AddressResolution(null, null);
        }

        static AddressResolution ok(MallAddress address) {
            return new AddressResolution(address, null);
        }

        static AddressResolution rejected(MallRedeemReason problem) {
            return new AddressResolution(null, problem);
        }
    }

    /**
     * 实物才需要收货地址，虚拟商品直接放行。
     *
     * <p>🔴 必须<b>重查</b>，不能信任传进来的 id：支付期间用户可能把这条地址删了
     *（DDL 里 address_id 是软引用，刻意不加外键）。查不到就拦下来让用户重选，
     * 别拿着一个空地址去建履约单 —— 那张单子发不出去，而失败原因会指向仓库。
     *
     * <p>查回来的地址要<b>带出去</b>而不是丢掉再查一次：这一步和落订单之间只隔几行，
     * 查两遍除了多一次往返没有任何好处。
     */
    private AddressResolution resolveAddress(MallRedeemCmd cmd, MallCommodity commodity) {
        if (!isPhysical(commodity)) {
            return AddressResolution.notNeeded();
        }
        if (cmd.addressId() == null) {
            return AddressResolution.rejected(MallRedeemReason.ADDRESS_REQUIRED);
        }
        MallAddress address = mallAddressService.getOwned(cmd.addressId(), cmd.memberId());
        return address == null
                ? AddressResolution.rejected(MallRedeemReason.ADDRESS_NOT_FOUND)
                : AddressResolution.ok(address);
    }

    /** 一次最多兑几件。不封的话一个 {@code quantity=99999} 会把库存条件判断变成一次巨额扣减 */
    private static int clampQuantity(Integer requested) {
        return Math.min(Math.max(requested == null ? 1 : requested, 1), MAX_QUANTITY);
    }

    /**
     * ① 占库存。条件 UPDATE，影响行数为 0 即失败。
     *
     * <p>积分+现金的单子钱还没收，只能<b>锁</b>住库存等支付回调；纯积分是同步扣的，
     * 直接<b>卖</b>掉。两条路走的是不同的 SQL，锁了不卖的那部分由超时释放 job 收回。
     */
    private boolean reserveStock(MallSku sku, int quantity, boolean hangs) {
        int rows = hangs ? mallSkuDao.lock(sku.getId(), quantity) : mallSkuDao.sell(sku.getId(), quantity);
        return rows > 0;
    }

    /**
     * ② 占限兑额度。没配限兑（null 或 0）视为不限，直接放行。
     *
     * <p>period_key 由 SQL 里的 {@code DATE_FORMAT(NOW(), ...)} 算 —— <b>数据库时钟</b>，
     * 不是 JVM 时钟。用 JVM 时间在跨时区部署时日切点对不上，用户能在某个时段多兑一次。
     */
    private boolean consumeExchangeLimit(Long memberId, MallCommodity commodity, int quantity) {
        Integer limitCount = commodity.getLimitCount();
        if (limitCount == null || limitCount <= 0) {
            return true;
        }
        return mallExchangeLimitDao.tryConsume(memberId, commodity.getId(),
                commodity.getLimitPeriod(), quantity, limitCount) > 0;
    }

    /**
     * ③ 扣积分。返回 null 表示成功，非 null 是<b>给用户看的</b>拒绝原因。
     *
     * <p>0 分商品直接跳过：0 是「免费兑换」的合法价格，不是「没设置」。
     *
     * <p>失败原因按 {@code AssetDebitReason} 逐个翻译，<b>不按 message 判</b> ——
     * 文案改一个字就静默失配，而失配的表现是「余额不足」被显示成「操作失败，请稍后再试」。
     */
    private MallRedeemReason debitPoints(Long memberId, MallCommodity commodity, String orderNo, int payPoints) {
        if (payPoints <= 0) {
            return null;
        }
        AssetDebitResult debit = assetDebitApi.debit(new AssetDebitCmd(
                memberId, "SCORE", BigDecimal.valueOf(payPoints),
                BIZ_TYPE, orderNo, "商城兑换 " + commodity.getCommodityName()));
        if (debit.accepted()) {
            return null;
        }
        return switch (debit.reason()) {
            case BALANCE_NOT_ENOUGH -> MallRedeemReason.POINTS_NOT_ENOUGH;
            case WALLET_UNAVAILABLE -> MallRedeemReason.WALLET_UNAVAILABLE;
            case CONCURRENT_CONFLICT -> MallRedeemReason.CONCURRENT_CONFLICT;
            // 会员不存在意味着调用方拿了个假 id —— 对用户是「服务出问题了」，不是他的错
            case MEMBER_NOT_FOUND, UNKNOWN -> MallRedeemReason.INTERNAL;
        };
    }

    /**
     * ⑤ 发起履约：在事务里<b>发事件</b>，在提交之后才真正执行。
     *
     * <p>🔴 发布不等于执行：{@code MallOrderFulfillListener} 听的是 AFTER_COMMIT，
     * 事务回滚时这个事件根本不会投递 —— 「单没落成但货已经发出去」在这个形状下不可能发生。
     *
     * <p>只有待履约才发：payType=2 的单子落在 0-待支付，钱还没收，现在发货就是白送。
     * 那条路要等支付回调把它推到 10，而支付链路至今一行代码都没有。
     */
    private void publishFulfillment(MallOrder order) {
        if (MallOrderStatusEnum.PENDING == order.getStatus()) {
            eventPublisher.publishEvent(new MallOrderPendingEvent(order.getOrderNo()));
            /*
             * 打点：这一单付掉了。
             *
             * 🔴 判据和履约<b>刻意是同一个</b>（status == PENDING），不是巧合：
             *    「资产已经结清」这件事同时决定了「可以发货」和「算不算消费」。
             *    payType=2 的混合单此刻落在 0-待支付，钱还没收 —— 它的 ORDER_PAID
             *    由 MallPayService 在支付确认时发，不在这里。
             *
             * 两个 if 写在一起而不是分开两处，是为了让将来改判据的人
             * 一次看到两个后果。分开写的话，改了履约那一半、忘了这一半，
             * 表现是「发货了但任务进度不涨」，而且不报错。
             */
            orderActionPublisher.publishOrderPaid(order);
        }
    }

    /**
     * 拒绝并<b>回滚</b>：前面几步可能已经占了库存/额度，靠事务撤销，不手工补偿。
     *
     * <p>之所以抛一个内部异常再在外面转成返回值 —— 不行，那样调用方拿不到原因。
     * 所以这里用 Spring 的编程式回滚标记：方法照常返回拒绝结果，但事务被标记为
     * rollback-only，出方法时统一回滚。
     */
    private MallRedeemResult reject(MallRedeemReason reason) {
        markRollbackOnly();
        return MallRedeemResult.ofReject(reason);
    }

    /**
     * 标记当前事务回滚。
     *
     * <h3>为什么单独抽一个方法，而不是把这一行写在 {@link #reject} 里</h3>
     * 为了让它<b>能被断言</b>。「占了库存之后的每一次拒绝都必须回滚」这条不变量，
     * 在此之前只写在注释里 —— 而 {@code TransactionAspectSupport.currentTransactionStatus()}
     * 读的是 Spring 内部的 ThreadLocal，单测里没有活动事务，一调就抛
     * {@code NoTransactionException}，于是所有拒绝分支<b>一条都测不了</b>。
     * 抽成实例方法之后，测试 spy 掉它就能逐条验证「这一路拒绝有没有标回滚」。
     *
     * <p>🔴 用 {@code setRollbackOnly()} 而不是抛异常，是因为调用方要拿到<b>拒绝原因</b>
     *（"积分不足" / "已达兑换上限"）。抛异常在方法内部 catch 掉不会触发回滚，
     * 让它穿出去调用方就只剩一个异常类型。所以只能是「正常返回 + 标记回滚」这一种形状。
     *
     * <p>⚠️ 这依赖 {@code redeem} 是<b>最外层</b>事务：此时 {@code setRollbackOnly()}
     * 置的是 local rollback-only，Spring 静默回滚，调用方照常拿到返回值。
     * 一旦有谁给 {@code MallClientFacade.redeem} 或更上游加了 {@code @Transactional}，
     * 它就变成 global rollback-only，外层边界会抛 {@code UnexpectedRollbackException} ——
     * 表现是「积分不足」这类用户文案全变成 500。
     */
    protected void markRollbackOnly() {
        org.springframework.transaction.interceptor.TransactionAspectSupport
                .currentTransactionStatus().setRollbackOnly();
    }

    private static boolean isVisible(MallCommodity commodity) {
        LocalDateTime now = LocalDateTime.now();
        return MallCommodityStatusEnum.ON == commodity.getStatus()
                && (commodity.getStartTime() == null || !now.isBefore(commodity.getStartTime()))
                && (commodity.getEndTime() == null || !now.isAfter(commodity.getEndTime()));
    }

    private static boolean isPhysical(MallCommodity commodity) {
        return "PHYSICAL".equals(commodity.getCommodityType());
    }

    /**
     * SKU 价为空则继承商品基准价。
     *
     * <p>DDL 刻意允许 NULL 而非默认 0 —— 0 是「免费兑换」的合法取值，
     * 用 0 当「未设置」就分不清「没填」和「真免费」了。
     */
    private static int resolvePoints(MallSku sku, MallCommodity commodity) {
        Integer skuPrice = sku.getSkuPointsPrice();
        if (skuPrice != null) {
            return skuPrice;
        }
        return commodity.getPointsPrice() == null ? 0 : commodity.getPointsPrice();
    }

    private static BigDecimal resolveCash(MallSku sku, MallCommodity commodity) {
        BigDecimal skuPrice = sku.getSkuCashPrice();
        if (skuPrice != null) {
            return skuPrice;
        }
        return commodity.getCashPrice() == null ? BigDecimal.ZERO : commodity.getCashPrice();
    }

    private MallOrder buildOrder(MallRedeemCmd cmd, MallCommodity commodity, MallSku sku,
                                 int quantity, String orderNo, int payPoints,
                                 boolean hangs, MallAddress address) {
        BigDecimal cashPrice = resolveCash(sku, commodity);
        MallOrder order = new MallOrder();
        order.setOrderNo(orderNo);
        order.setMemberId(cmd.memberId());
        // 展示快照：记的是【下单当时】那个账号，不是这人现在叫什么。审计要回答的是「当时是谁」
        order.setMemberName(memberService.requireMemberName(cmd.memberId()));

        order.setCommodityId(commodity.getId());
        order.setCommodityCode(commodity.getCommodityCode());
        order.setSkuId(sku.getId());
        order.setSkuCode(sku.getSkuCode());

        /*
         * 🔴 下面全是快照，不是外键读取。
         * 运营下周把「T恤」改名、把 5000 分调成 8000 分，历史订单必须还长原来的样子 ——
         * 靠 join 商品表拿名字和价格，改一次价历史全乱。
         */
        order.setCommodityType(commodity.getCommodityType());
        order.setAssetRef(commodity.getAssetRef());
        order.setCommodityName(commodity.getCommodityName());
        order.setCoverFileId(commodity.getCoverFileId());
        order.setSkuAttrs(sku.getSkuAttrs());
        order.setQuantity(quantity);
        order.setPointsPrice(resolvePoints(sku, commodity));
        order.setCashPrice(cashPrice);
        order.setPayPoints(payPoints);
        order.setPayCash(cashPrice.multiply(BigDecimal.valueOf(quantity)));

        /*
         * 🔴 只存 address_id 软引用，收件人姓名/电话/门牌<b>不进订单表</b>。
         * 快照的归宿是履约单（t_physical_delivery），不是订单：
         * 存两份等于同一份个人信息存两处（加密两遍、脱敏两遍、注销清理两处），
         * 而且会不一致 —— 运营在发货单上改了地址，订单上还是老的，
         * 客服看两处得到两个答案且无从判断哪个是真的。
         */
        order.setAddressId(address == null ? null : address.getId());

        if (hangs) {
            // 积分+现金要等支付回调，中间悬着；到点由超时释放 job 取消并放回库存
            order.setStatus(MallOrderStatusEnum.UNPAID);
            order.setExpireTime(LocalDateTime.now().plusMinutes(PAY_EXPIRE_MINUTES));
        } else {
            // 纯积分是同步扣的，不存在悬挂：直接进待履约
            order.setStatus(MallOrderStatusEnum.PENDING);
            order.setPayTime(LocalDateTime.now());
        }
        // 秒杀那两列对普通订单填 NORMAL 即可（DDL 里为将来的场次预留）
        order.setSourceType("NORMAL");
        return order;
    }

    /**
     * 订单号：<b>时间有序</b> + 随机尾巴。
     *
     * <p>刻意不用 {@code SolvelaCodeUtil} 的 10 位随机业务编码 ——
     * 那套是给「运营会念出来、要跨环境稳定」的配置类编码用的（活动、商品、SKU）。
     * 订单号是<b>单据号</b>：客服拿到它第一件事是想知道「什么时候下的」，
     * 而且它会被按时间范围查询与归档，随机串在这两件事上都帮倒忙。
     *
     * <p>长度 1 + 17 + 6 = 24，{@code order_no varchar(32)} 装得下。
     */
    private static String generateOrderNo() {
        StringBuilder sb = new StringBuilder(ORDER_NO_PREFIX);
        sb.append(LocalDateTime.now().format(ORDER_NO_TIME));
        for (int i = 0; i < 6; i++) {
            sb.append(Character.toUpperCase(Character.forDigit(RANDOM.nextInt(36), 36)));
        }
        return sb.toString();
    }
}
