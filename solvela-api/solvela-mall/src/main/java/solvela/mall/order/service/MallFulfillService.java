package solvela.mall.order.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import solvela.enums.NotificationTemplateEnum;
import solvela.notification.domain.NotifyRequest;
import solvela.notification.service.NotificationService;
import solvela.mall.MallAddress;
import solvela.mall.MallOrder;
import solvela.mall.address.service.MallAddressService;
import solvela.mall.order.dao.MallOrderDao;
import solvela.member.api.AssetGrantApi;
import solvela.member.api.AssetGrantCmd;
import solvela.member.api.AssetGrantReason;
import solvela.member.api.AssetGrantResult;

/**
 * 履约：把用户兑到的东西真正发出去。
 *
 * <p>状态机 {@code 0 →(支付/直接扣分)→ 10 →(投递履约)→ 20 →(履约回执)→ 30/60}
 * 里的<b>后半段</b>。前半段在 {@link MallRedeemService}，两段刻意<b>不在同一个事务</b>：
 * 把发货单/发券塞进付款那个事务，等于让一次外部调用决定用户的积分扣不扣得成。
 *
 * <h3>🔴 幂等靠 markFulfilling 那次条件 UPDATE，不靠下游</h3>
 * {@code 10 → 20} 是一次 CAS：并发下只有一个线程能把它改成功，其余拿到 0 行直接退出。
 * 这不是锦上添花 —— {@code t_member_coupon} 上<b>没有</b>
 * {@code UNIQUE(source_type, source_biz_id)}，重复发券在库这一层拦不住，
 * 全靠这里。（实物有 {@code uk_t_biz_phy_dlv_src}、钱包有
 * {@code UNIQUE(biz_ref_id, asset_type)}，那两条路是双保险。）
 *
 * <h3>🔴 拒绝 → 标 60；异常 → 回滚到 10</h3>
 * 这是本类唯一需要想清楚的分支：
 * <ul>
 *   <li><b>{@link AssetGrantResult#accepted()} 为 false</b> —— 重试也没用
 *      （商品漏配券模、地址被删）。标成 60-履约失败，把原因写进
 *      {@code fail_reason}，等人来看。</li>
 *   <li><b>抛异常</b> —— 可能是数据库抖了一下。让事务回滚，
 *      {@code status} 退回 10，下一轮重试还能接手。</li>
 * </ul>
 * 混成一种的后果是二选一：要么永远重试一个永远不会成功的单子，
 * 要么一次网络抖动就让用户的东西彻底发不出来。
 *
 * <h3>🔴 履约失败不退积分</h3>
 * 东西还欠着用户，不是没买。退了等于单方面替用户取消订单，
 * 而运营补个券模配置就能重发。真要取消是另一条路（40 + 退款）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MallFulfillService {

    /** 履约单上的来源类型。运营的发货台按它区分「中奖寄出」和「商城兑换」 */
    private static final String SOURCE_TYPE_MALL = "MALL";

    private static final String BIZ_TYPE = "MALL_EXCHANGE";

    /** 现金/红包。见 fulfill 里那段红字：面额在 DDL 里没有归宿，暂不履约 */
    private static final String ASSET_TYPE_BALANCE = "BALANCE";

    /** {@code fail_reason} 是 varchar(255)，超了 MySQL 严格模式会整条拒绝 */
    private static final int FAIL_REASON_MAX = 255;

    private final MallOrderDao mallOrderDao;
    private final MallAddressService mallAddressService;
    private final AssetGrantApi assetGrantApi;
    private final NotificationService notificationService;

    /**
     * 履约一单。<b>可以随便重复调</b> —— 抢不到 {@code 10 → 20} 就什么都不做。
     *
     * <h3>🔴 必须是 REQUIRES_NEW</h3>
     * 本方法由 {@code AFTER_COMMIT} 触发，那时外层事务<b>已经提交但尚未彻底解绑</b>：
     * 事务同步器还活着，连接资源还挂在线程上。此时用默认的 REQUIRED，
     * Spring 会「加入」那个已经完成的事务 —— 表现是<b>写操作静默不提交</b>，
     * 没有异常、没有日志，单子永远停在 20-履约中。
     * 这是 AFTER_COMMIT 里最经典的一个坑，REQUIRES_NEW 是它的解。
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void fulfill(String orderNo) {
        // 抢闸门：markFulfilling 是 WHERE status=10 的条件更新，抢不到说明别人在做或已经做过。
        // t_member_coupon 上没有唯一键，重复发券在库那层拦不住 —— 这次 CAS 是唯一的闸门
        if (mallOrderDao.markFulfilling(orderNo) == 0) {
            log.debug("【商城履约】{} 不在待履约状态，跳过", orderNo);
            return;
        }

        MallOrder order = mallOrderDao.getByOrderNo(orderNo);
        if (order == null) {
            // 上一行刚 UPDATE 成功，这一行就查不到 —— 不可能，除非有人在并发删单
            log.error("【商城履约】{} 抢到了闸门却查不到订单", orderNo);
            return;
        }

        if (rejectIfBalanceCommodity(orderNo, order)) {
            return;
        }

        settle(orderNo, order, assetGrantApi.grant(buildCmd(order)));
    }

    /**
     * BALANCE 类商品当场标失败。<b>这是挡下，不是漏了。</b>
     *
     * <p>DDL 至今没给「面额」一个归宿：{@code t_mall_commodity} 上 points_price 是
     * <b>要花的</b>积分、cash_price 是<b>要付的</b>现金、original_price 是划线展示价，
     * 没有一列是「兑到手多少钱」。asset_ref 那行注释写的「BALANCE 存面额来源标识」
     * 是个占位，从来没定过。
     *
     * <p>拿 original_price 顶替能跑，但那等于让「前端划线展示的价格」决定真实发多少钱 ——
     * 运营改一次展示文案就是一次资损。宁可发不出去、留一条运营看得懂的失败原因，也不猜。
     * 定下来之后：商品表加一列面额 → 下单时快照进订单 → 这里传给 amount。
     *
     * @return true 表示已经标失败并处理完毕，调用方应当停止
     */
    private boolean rejectIfBalanceCommodity(String orderNo, MallOrder order) {
        if (!ASSET_TYPE_BALANCE.equals(order.getCommodityType())) {
            return false;
        }
        String reason = "现金/红包商品尚不支持履约：商品表还没有「兑到手面额」这一列，"
                + "请勿把商品配成 BALANCE 类型";
        mallOrderDao.markFailed(orderNo, StringUtils.abbreviate(reason, FAIL_REASON_MAX));
        log.error("【商城履约失败】{} 商品[{}] 被配成了 BALANCE，而面额无处可取",
                orderNo, order.getCommodityCode());
        return true;
    }

    /**
     * 回执落库：20-履约中 → 30-已完成 / 60-履约失败。
     *
     * <p>🔴 <b>失败不退积分</b> —— 东西还欠着用户，不是没买。退了等于把一次
     * 「我们没发出去」变成「这单不算数」，而用户那边看到的是积分回来了、东西没了。
     *
     * <h3>🔴 同理，失败也<b>不放券</b>（2026-09-15 阶段 4）</h3>
     * 这里刻意<b>没有</b>调 {@code CouponWriteOffApi.release}。券在下单落成
     * 待履约的那一刻就已经确认掉了（{@code MallRedeemService.confirmCouponIfSettled}）——
     * 因为那一刻积分已经扣了、没有回头路。
     *
     * <p>只放券不退积分，用户会拿到一个自相矛盾的结果：券回来了，积分没回来。
     * 两个一起退，那就是把「我们欠着你」变成了「这单不算数」，而这条路
     * 刻意不这么做。所以<b>两个都不动</b>是唯一自洽的选择。
     *
     * <p>真正会放券的是超时取消那条路（{@code MallOrderCancelService}），
     * 它同时退积分 —— 那一条才是「这单不算数」。
     */
    private void settle(String orderNo, MallOrder order, AssetGrantResult result) {
        if (result.accepted()) {
            mallOrderDao.markFinished(orderNo, result.fulfillRefId());
            log.info(">>>> [商城履约完成] {} {} -> {}", orderNo, order.getCommodityType(), result.fulfillRefId());
            return;
        }
        String failReason = describe(result.reason());
        mallOrderDao.markFailed(orderNo, failReason);
        // 用户付了积分却没拿到东西，这一条必须能被告警抓到
        log.error("【商城履约失败】{} 商品[{}] 原因: {}", orderNo, order.getCommodityCode(), failReason);
        notifyFulfillFailed(orderNo, order, failReason);
    }

    /**
     * 履约失败告诉用户一声。
     *
     * <p>🔴 <b>文案里绝不能说「积分已退回」</b>：本方法所在的这条路<b>刻意不退积分</b>
     * （见 {@code MallOrderDao.markFailed} 的注释 —— 东西还欠着用户，不是没买，
     * 运营补配一下券模就能重发）。真正的退款是超时取消那条路，用的是另一个模板
     * {@code ORDER_CANCELLED}。
     *
     * <p>两条文案混了，用户会按「钱回来了」去理解一次「我们欠着你」，然后再兑一单。
     */
    private void notifyFulfillFailed(String orderNo, MallOrder order, String failReason) {
        notificationService.send(NotifyRequest.of(NotificationTemplateEnum.ORDER_FULFILL_FAILED, order.getMemberId())
                .param("orderNo", orderNo)
                .param("commodityName", order.getCommodityName())
                .param("failReason", failReason)
                .bizRefId(orderNo)
                .build());
    }

    /**
     * 组装发放指令。
     *
     * <p>商品信息全部取<b>订单上的快照</b>，不回查商品表：
     * 运营在下单之后把商品改成了别的类型，也不该影响这一单该发什么。
     */
    private AssetGrantCmd buildCmd(MallOrder order) {
        MallAddress address = resolveAddress(order);
        return new AssetGrantCmd(
                order.getMemberId(),
                order.getCommodityType(),
                order.getAssetRef(),
                order.getCommodityName(),
                order.getQuantity(),
                // 面额：PHYSICAL / COUPON 都不看它，而 BALANCE 在上面已经被挡下了
                null,
                SOURCE_TYPE_MALL,
                order.getOrderNo(),
                BIZ_TYPE,
                address == null ? null : address.getReceiverName(),
                address == null ? null : address.getReceiverPhone(),
                address == null ? null : fullAddress(address),
                "商城兑换 " + order.getCommodityName());
    }

    /**
     * 收件信息在这里<b>才</b>从地址簿读出来。
     *
     * <p>订单表只存 {@code address_id} 软引用，收件三要素不进订单 ——
     * 快照的归宿是履约单，不是订单（存两份等于同一份个人信息加密两遍、
     * 注销时清理两处，而且会不一致）。
     *
     * <p>取不到就返回 null，交给发放侧拒绝并标 60：用户在下单到履约之间
     * 把地址删了是可能的（毫秒级，但可能）。<b>不要拿空地址去建履约单</b> ——
     * 那张单子会一直躺在发货台上，没人知道该寄到哪。
     */
    private MallAddress resolveAddress(MallOrder order) {
        if (order.getAddressId() == null) {
            return null;
        }
        return mallAddressService.getOwned(order.getAddressId(), order.getMemberId());
    }

    /** 省市区 + 详细门牌拼成一条。履约单只有一个地址列，运营看的也是这一条 */
    private static String fullAddress(MallAddress address) {
        String joined = StringUtils.defaultString(address.getProvince())
                + StringUtils.defaultString(address.getCity())
                + StringUtils.defaultString(address.getDistrict())
                + StringUtils.defaultString(address.getDetailAddress());
        return StringUtils.trimToNull(joined);
    }

    /**
     * 拒绝原因 → 写进 {@code fail_reason} 的话。
     *
     * <p>这句话是<b>给运营看的</b>，不是给用户看的：它要能直接指出该去改哪里。
     * 用 switch 表达式且<b>不写 default</b> —— 资产域新增一个拒绝原因时这里
     * 编译不过，而不是悄悄落进「未知错误」，让运营对着一单不知道该修什么。
     */
    private static String describe(AssetGrantReason reason) {
        String text = switch (reason) {
            case UNSUPPORTED_ASSET_TYPE -> "商品类型没有发放通道，请检查商品配置的类型";
            case ASSET_REF_REQUIRED -> "券商品未配置券模编码（asset_ref），补齐后可重发";
            case RECEIVER_REQUIRED -> "收货地址已失效，需联系用户重新提供";
            case AMOUNT_INVALID -> "现金/红包商品的面额不是正数，请检查商品定价";
            case MEMBER_NOT_FOUND -> "会员不存在";
            case WALLET_UNAVAILABLE -> "会员钱包被冻结，解冻后可重发";
            case UNKNOWN -> "资产域返回未归类的拒绝，去营销/账务服务日志里按订单号查";
        };
        return StringUtils.abbreviate(text, FAIL_REASON_MAX);
    }
}
