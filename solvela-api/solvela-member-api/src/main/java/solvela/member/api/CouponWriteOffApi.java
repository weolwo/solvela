package solvela.member.api;

import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

/**
 * 券的<b>三阶段核销</b>：试算 → 锁定 → 确认 / 释放。实现在 {@code solvela-ledger}。
 *
 * <h3>为什么要有这个契约，而不是让 mall 直接调 CouponWriteOffService</h3>
 * {@code MallLedgerBoundaryTest} 一个字都不让商城认识 {@code solvela/ledger/} ——
 * 目标形态是<b>资产独立成服务</b>，而券是资产。直接调今天完全跑得通，
 * 坏的是拆的那一天：那些调用点要一个个找出来重写，而它们不会有任何标记。
 *
 * <p>形状与 {@link AssetDebitApi} 完全一致：今天在本进程里解析成
 * {@code CouponWriteOffApiService}，拆出去之后解析成 HTTP 代理，<b>调用方一行不改</b>。
 *
 * <h3>🔴 为什么是三个动作而不是一个 useCoupon</h3>
 * 用户下单用了券，订单后来失败或超时取消 —— <b>券白没了</b>。
 * 积分有 {@code debit}/{@code refund} 一对，券也必须有 {@code lock}/{@code release} 一对。
 * 一步核销的 API 在签名上就表达不了「还回来」这件事。
 *
 * <h3>🔴 这个接口是一道闸门，别把它接到网关上</h3>
 * 它能直接消耗用户的券 —— 和 {@link AssetDebitApi} 同一条规矩，<b>只给服务端内部调</b>。
 *
 * <p>只读的那一半（券包、试算）在 {@link CouponQueryApi} 里，
 * 那个才是网关能用的。这个拆法和 {@link AssetApi} / {@link AssetDebitApi} 完全一致：
 * <b>能读的和能改的分成两个接口</b>，这样「网关不该拿到写能力」这件事
 * 是由类型系统表达的，而不是靠谁记得。
 */
@HttpExchange("/internal/coupon")
public interface CouponWriteOffApi {

    /**
     * 锁定：0-未使用 → 4-锁定中。提交订单时调，<b>放在扣款之前</b>。
     *
     * <p>它和库存、限购是同一类「已占资源」，失败要一起回滚。
     *
     * <p>⚠️ 调用方<b>必须</b>把这个调用和自己的业务写在<b>同一个事务</b>里。
     * 今天同进程，事务能穿透；拆成服务之后这里要改成 saga —— 那一天到来时，
     * 这条注释就是要回来看的地方。
     *
     * <p>幂等键是 {@code bizRefId}：同一笔单重复锁定，看到已经是自己锁的就直接放行。
     */
    @PostExchange("/lock")
    CouponWriteOffView lock(@RequestBody CouponLockCmd cmd);

    /**
     * 确认：4-锁定中 → 1-已使用。资产已经结清、这一单再也回不去的时候调。
     *
     * <h3>⚠️ 返回失败时调用方<b>必须</b>处理，不能当没看见</h3>
     * 最可能的原因是<b>兜底任务已经把这张券释放了</b> —— 也就是说这一单实际上
     * 没有用券。当没看见的话，订单按打折后的金额结算，而券还躺在用户券包里
     * 可以<b>再用一次</b>。
     */
    @PostExchange("/confirm")
    CouponWriteOffView confirm(@RequestBody CouponWriteOffCmd cmd);

    /**
     * 释放：4-锁定中 → 0-未使用。订单取消 / 支付超时时调。
     *
     * <p>幂等：已经放回去了直接返回成功 —— 两条取消路径同时跑到是常态。
     *
     * <p>⚠️ {@code bizRefId} 必须和锁定时<b>一样</b>，这一点和
     * {@link AssetDebitApi#refund} 刚好相反（那边要求不同，否则被唯一键当重复挡掉）。
     * 这里的条件更新是 {@code WHERE locked_biz_id = ?} ——
     * 传了别的单号就什么都不会发生，而且不报错。
     */
    @PostExchange("/release")
    CouponWriteOffView release(@RequestBody CouponWriteOffCmd cmd);
}
