package solvela.mall.order.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
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
        // ---- ① 抢闸门。抢不到说明别人在做，或者已经做过 ----
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

        /*
         * ---- ② 发放 ----
         *
         * 🔴 BALANCE 在这里挡下，不是漏了。
         * DDL 至今没给「面额」一个归宿：t_mall_commodity 上 points_price 是<b>要花的</b>积分、
         * cash_price 是<b>要付的</b>现金、original_price 是划线展示价，
         * 没有一列是「兑到手多少钱」。asset_ref 那行注释写的「BALANCE 存面额来源标识」
         * 是个占位，从来没定过。
         *
         * 拿 original_price 顶替是能跑，但那等于让「前端划线展示的价格」决定真实发多少钱 ——
         * 运营改一次展示文案就是一次资损。所以宁可发不出去、留一条运营看得懂的失败原因，
         * 也不猜。定下来之后：商品表加一列面额 → 下单时快照进订单 → 这里传给 amount。
         */
        if (ASSET_TYPE_BALANCE.equals(order.getCommodityType())) {
            String reason = "现金/红包商品尚不支持履约：商品表还没有「兑到手面额」这一列，"
                    + "请勿把商品配成 BALANCE 类型";
            mallOrderDao.markFailed(orderNo, StringUtils.abbreviate(reason, FAIL_REASON_MAX));
            log.error("【商城履约失败】{} 商品[{}] 被配成了 BALANCE，而面额无处可取",
                    orderNo, order.getCommodityCode());
            return;
        }

        AssetGrantResult result = assetGrantApi.grant(buildCmd(order));

        // ---- ③ 回执 ----
        if (result.accepted()) {
            mallOrderDao.markFinished(orderNo, result.fulfillRefId());
            log.info(">>>> [商城履约完成] {} {} -> {}", orderNo, order.getCommodityType(), result.fulfillRefId());
            return;
        }
        String failReason = describe(result.reason());
        mallOrderDao.markFailed(orderNo, failReason);
        // 用户付了积分却没拿到东西，这一条必须能被告警抓到
        log.error("【商城履约失败】{} 商品[{}] 原因: {}", orderNo, order.getCommodityCode(), failReason);
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
