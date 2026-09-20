package solvela.member.api;

import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

import java.util.List;

/**
 * 实物履约单：<b>会员自己那一侧</b>。
 *
 * <h3>为什么契约在 member-api</h3>
 * {@code t_physical_delivery} 属于资产域（solvela-ledger），而资产域将来和会员
 * 同属一个服务 —— 那个 pom 里写着「不会再有 solvela-ledger-api」。
 * 所以它和 {@code AssetApi} / {@code CouponQueryApi} 共用这一份契约。
 *
 * <h3>🔴 补填收件信息为什么不在这里收 addressId</h3>
 * 地址簿（{@code t_mall_address}）是<b>商城</b>的表，而 marketing ↮ ledger 那条缝
 * 由 {@code MallLedgerBoundaryTest} / {@code LedgerBoundaryTest} 两边守着 ——
 * 资产域拿到一个 addressId 也查不出人来。
 *
 * <p>所以分工照 {@code MallFulfillService} 已经跑通的那条路走：
 * <b>谁拥有地址，谁负责解析成三个字段</b>，再通过契约把明文传过来
 * （那边调 {@code AssetGrantApi} 时传的就是 receiverName/Phone/Address 三个字段）。
 * C 端那一步走 {@code MallApi.fillDeliveryAddress}，它解析完再调本接口的 {@link #fillReceiver}。
 *
 * <p>本接口<b>只收明文三件套</b>，不认识地址簿 —— 这条边界不是形式，
 * 它是「资产域独立成服务」那天不用回头重写的前提。
 *
 * @author alaric
 * @date 2026-09-18
 */
@HttpExchange("/internal/delivery")
public interface DeliveryApi {

    /**
     * 我的实物履约单，新的在前。
     *
     * <p>中奖的实物和商城兑换的实物<b>混在一起给</b> —— 用户不关心「这件东西是抽中的
     * 还是兑的」，他只想知道「我的东西呢」。要区分看 {@code sourceType}。
     *
     * <p>没有就返回空数组，不是 404。
     */
    @GetExchange
    List<MemberDeliveryView> listMine(@RequestParam Long memberId, @RequestParam int limit);

    /**
     * 补填收件信息。
     *
     * <h3>为什么这件事必须存在</h3>
     * {@code PhysicalDelivery.receiverName} 的注释写着「中奖时未知，<b>由用户后续补填</b>
     * —— 所以可空，不是忘了加约束」。那句话挂了很久，而补填的入口一直不存在：
     * 中了实物奖之后用户侧是断的，只能靠客服手工改库。
     *
     * <h3>🔴 只有「待发货且还没填」的单能填</h3>
     * 已发货之后不许改 —— 货已经在路上了，改地址只会让用户以为改成功了。
     * 这个判据由服务端给（{@link MemberDeliveryView#needAddress}），端上不要自己推。
     *
     * <p>幂等：重复提交同样的地址不报错，返回同一个结果。
     */
    @PostExchange("/receiver")
    DeliveryFillResult fillReceiver(@RequestBody DeliveryReceiverCmd cmd);
}
