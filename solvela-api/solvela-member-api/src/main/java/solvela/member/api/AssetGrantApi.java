package solvela.member.api;

import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

/**
 * 资产的<b>发放（履约）</b>。实现在 {@code solvela-ledger}。
 *
 * <h3>和 {@link AssetDebitApi} 的分工：方向相反</h3>
 * 那个是「出」—— 用户花掉自己的资产（商城扣积分）。
 * 这个是「入」—— 把用户买到的东西真正给他：实物落发货单、券落券包、现金落钱包。
 * 两件事在商城的一次兑换里<b>都会发生</b>，但<b>不在同一个事务里</b>，
 * 这正是分成两个接口的原因（见 {@link #grant} 上的说明）。
 *
 * <h3>为什么不复用 {@code IAssetHandler} 那套</h3>
 * 履约的落库逻辑 ledger 里已经有了（{@code PhysicalAssetHandler} / {@code CouponAssetHandler}），
 * 但它们的入参是 {@code ProposalRecord} —— <b>提案</b>。而
 * mall.sql 开头明写「🔴 商城不走 t_proposal_record」：商城是用户花自己的积分，
 * 走审批没道理。为了复用而硬造一条假提案，还得硬编一条假的优惠配置
 *（{@code promotion_config_id} 是 NOT NULL），那是拿数据的正确性换代码行数。
 *
 * <p>所以这里是<b>第二个入口，同一批表</b>：写的是同样的
 * {@code t_physical_delivery} / {@code t_member_coupon} / {@code t_member_wallet}，
 * 运营的发货台、物流导入、券管理完全复用 —— DDL 里
 * 「商城以 source_type='MALL' 写入即可」说的就是这条路。
 *
 * <h3>🔴 这个接口能凭空产生资产，别把它接到网关上</h3>
 * 和 {@link AssetDebitApi} 同一条规矩，而且更严重：那个只能减，这个能增。
 * {@link AssetApi} 之所以刻意只读，就是因为它是绑在网关上的那个。
 */
@HttpExchange("/internal/asset")
public interface AssetGrantApi {

    /**
     * 发放。按 {@code cmd.assetType} 分派到对应的资产通道。
     *
     * <h3>⚠️ 不要把它和扣减放进同一个事务</h3>
     * 扣积分是「用户付款」，发放是「我们交货」。把交货塞进付款那个事务，
     * 等于让一次外部调用（写发货单 / 发券）决定用户的积分扣不扣得成 ——
     * 发券那一步抖一下，用户看到的是「兑换失败」，而他本来该拿到东西。
     *
     * <p>正确的形状是商城那边的状态机：{@code 10-待履约 →(调本接口)→ 20-履约中 → 30/60}。
     * 付款事务提交后才发起履约，失败留在 60 等重试，<b>积分不回退</b>（东西还欠着，不是没买）。
     *
     * <h3>幂等由调用方的单据状态保证</h3>
     * 实物这一侧有 {@code uk_t_biz_phy_dlv_src(source_biz_id, source_type)} 兜底，
     * 现金这一侧有 {@code UNIQUE(biz_ref_id, asset_type)} 兜底。
     * <b>但券那张表今天没有唯一键</b>（{@code t_member_coupon} 只有普通索引
     * {@code idx_source}），所以调用方必须先用一次条件 UPDATE 抢到「履约中」再调本接口 ——
     * 商城的 {@code MallFulfillService} 就是这么做的。
     *
     * <p>🔴 收件三要素是<b>明文</b>传的，落库时由 ledger 侧的 PiiTypeHandler 加密。
     * 这个接口只跑在服务端内部，不要把它的入参写进任何面向用户的日志。
     */
    @PostExchange("/grant")
    AssetGrantResult grant(@RequestBody AssetGrantCmd cmd);
}
