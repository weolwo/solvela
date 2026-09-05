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
import solvela.mall.order.event.MallOrderPendingEvent;
import solvela.mall.order.manager.MallOrderManager;
import solvela.mall.sku.dao.MallSkuDao;
import solvela.mall.sku.manager.MallSkuManager;
import solvela.marketing.api.MallRedeemCmd;
import solvela.marketing.api.MallRedeemReason;
import solvela.marketing.api.MallRedeemResult;
import solvela.member.api.AssetDebitApi;
import solvela.member.api.AssetDebitCmd;
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
    private final ApplicationEventPublisher eventPublisher;

    @Transactional(rollbackFor = Exception.class)
    public MallRedeemResult redeem(MallRedeemCmd cmd) {
        int quantity = Math.min(Math.max(cmd.quantity() == null ? 1 : cmd.quantity(), 1), MAX_QUANTITY);

        MallSku sku = mallSkuManager.getById(cmd.skuId());
        if (sku == null) {
            return MallRedeemResult.ofReject(MallRedeemReason.SKU_NOT_FOUND);
        }
        MallCommodity commodity = mallCommodityManager.getById(sku.getCommodityId());
        if (commodity == null || !isVisible(commodity)) {
            // 已下架与不存在给同一个原因：对用户都是「兑不了」
            return MallRedeemResult.ofReject(MallRedeemReason.COMMODITY_OFF);
        }

        // 实物必须有地址，且必须是这个会员自己的
        MallAddress address = null;
        if (isPhysical(commodity)) {
            if (cmd.addressId() == null) {
                return MallRedeemResult.ofReject(MallRedeemReason.ADDRESS_REQUIRED);
            }
            address = mallAddressService.getOwned(cmd.addressId(), cmd.memberId());
            if (address == null) {
                /*
                 * 🔴 支付期间用户可能把这条地址删了（DDL 里 address_id 是软引用，
                 * 刻意不加外键）。所以这里必须重查一次，查不到就拦下来让用户重选，
                 * 不要拿着一个空地址去建履约单。
                 */
                return MallRedeemResult.ofReject(MallRedeemReason.ADDRESS_NOT_FOUND);
            }
        }

        // ---- ① 占库存。条件 UPDATE，affected rows = 0 即失败 ----
        boolean hangs = MallPayTypeEnum.POINTS_CASH == commodity.getPayType();
        int stockRows = hangs
                ? mallSkuDao.lock(sku.getId(), quantity)
                : mallSkuDao.sell(sku.getId(), quantity);
        if (stockRows == 0) {
            return MallRedeemResult.ofReject(MallRedeemReason.OUT_OF_STOCK);
        }

        // ---- ② 占限兑额度 ----
        Integer limitCount = commodity.getLimitCount();
        if (limitCount != null && limitCount > 0) {
            int limitRows = mallExchangeLimitDao.tryConsume(cmd.memberId(), commodity.getId(),
                    commodity.getLimitPeriod(), quantity, limitCount);
            if (limitRows == 0) {
                // 抛出去让事务回滚，把 ① 占的库存一起撤掉 —— 手工回滚容易漏
                return reject(MallRedeemReason.EXCHANGE_LIMITED);
            }
        }

        // ---- ③ 扣积分。订单号先生成，它同时是幂等键 ----
        String orderNo = generateOrderNo();
        int payPoints = resolvePoints(sku, commodity) * quantity;
        if (payPoints > 0) {
            AssetDebitResult debit = assetDebitApi.debit(new AssetDebitCmd(
                    cmd.memberId(), "SCORE", BigDecimal.valueOf(payPoints),
                    BIZ_TYPE, orderNo, "商城兑换 " + commodity.getCommodityName()));
            if (!debit.accepted()) {
                return reject(switch (debit.reason()) {
                    case BALANCE_NOT_ENOUGH -> MallRedeemReason.POINTS_NOT_ENOUGH;
                    case WALLET_UNAVAILABLE -> MallRedeemReason.WALLET_UNAVAILABLE;
                    case CONCURRENT_CONFLICT -> MallRedeemReason.CONCURRENT_CONFLICT;
                    // 会员不存在意味着调用方拿了个假 id —— 对用户是「服务出问题了」
                    case MEMBER_NOT_FOUND, UNKNOWN -> MallRedeemReason.INTERNAL;
                });
            }
        }

        // ---- ④ 落订单。商品信息全部是快照，之后与商品表脱钩 ----
        MallOrder order = buildOrder(cmd, commodity, sku, quantity, orderNo, payPoints, hangs, address);
        mallOrderManager.save(order);

        /*
         * ---- ⑤ 发起履约。在事务里发事件，在 AFTER_COMMIT 里真正执行 ----
         *
         * 🔴 发布不等于执行：MallOrderFulfillListener 听的是 AFTER_COMMIT，
         * 所以事务回滚时这个事件根本不会投递 ——
         * “单没落成但货已经发出去”在这个形状下是不可能的。
         *
         * 只有待履约才发：payType=2 的单子落在 0-待支付，钱还没收，
         * 现在发货就是白送。那条路要等支付回调把它推到 10，
         * 而支付链路至今一行代码都没有。
         */
        if (MallOrderStatusEnum.PENDING == order.getStatus()) {
            eventPublisher.publishEvent(new MallOrderPendingEvent(orderNo));
        }

        return MallRedeemResult.ofAccepted(orderNo, order.getStatus());
    }

    /**
     * 拒绝并<b>回滚</b>：前面几步可能已经占了库存/额度，靠事务撤销，不手工补偿。
     *
     * <p>之所以抛一个内部异常再在外面转成返回值 —— 不行，那样调用方拿不到原因。
     * 所以这里用 Spring 的编程式回滚标记：方法照常返回拒绝结果，但事务被标记为
     * rollback-only，出方法时统一回滚。
     */
    private static MallRedeemResult reject(MallRedeemReason reason) {
        org.springframework.transaction.interceptor.TransactionAspectSupport
                .currentTransactionStatus().setRollbackOnly();
        return MallRedeemResult.ofReject(reason);
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
