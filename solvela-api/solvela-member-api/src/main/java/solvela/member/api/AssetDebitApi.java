package solvela.member.api;

import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

/**
 * 资产的<b>直接扣减 / 退还</b>。实现在 {@code solvela-ledger}。
 *
 * <h3>为什么不走提案（MemberProposalApi）</h3>
 * 提案带审批、预算与风控 —— 那是<b>发钱的闸门</b>：营销给用户发奖必须过它。
 * 而商城兑换是<b>用户花自己的积分</b>，走审批没道理
 *（mall.sql 开头明写「🔴 商城不走 t_proposal_record」）。
 * 方向也相反：提案是「入」，这里是「出」。
 *
 * <h3>为什么不让 mall 直接依赖 solvela-ledger</h3>
 * {@code LedgerBoundaryTest} 守着 marketing ↔ ledger 那条<b>将来的服务边界</b>：
 * 目标形态是资产独立成服务。让 mall 直接调 {@code MemberWalletService} 今天完全跑得通，
 * 坏的是拆的那一天 —— 那些调用点要一个个找出来重写，而它们不会有任何标记。
 *
 * <p>走这个契约的话，今天它在本进程里解析成 {@code AssetDebitApiService}，
 * 拆出去之后解析成 HTTP 代理，<b>调用方一行不改</b>。
 * 与 {@link MemberProposalApi} 是同一个形状。
 *
 * <h3>🔴 这个接口是一道闸门，别把它接到网关上</h3>
 * 它能直接减少用户的资产。{@link AssetApi} 之所以刻意只读，就是因为那个
 * 是绑在网关上的；<b>这个只给服务端内部调</b>。
 * 网关的 pom 里有 member-api，所以类路径上看得见它 —— 看得见不等于可以注入，
 * 加 bean 之前先想清楚为什么公网入口需要一个「扣钱」的能力。
 */
@HttpExchange("/internal/asset")
public interface AssetDebitApi {

    /**
     * 扣减。<b>幂等键是 {@code cmd.bizRefId}</b>，重复提交由
     * {@code UNIQUE(biz_ref_id, asset_type)} 挡住。
     *
     * <p>⚠️ 调用方<b>必须</b>把这个调用和自己的业务写在<b>同一个事务</b>里，
     * 否则「扣了分但订单没落」或者反过来。今天同进程，事务能穿透；
     * 拆成服务之后这里要改成 saga 或本地消息表 —— 那一天到来时，
     * 这条注释就是要回来看的地方。
     */
    @PostExchange("/debit")
    AssetDebitResult debit(@RequestBody AssetDebitCmd cmd);

    /**
     * 退还（兑换取消、超时释放、履约失败）。
     *
     * <p>钱包不存在时会自愈初始化 —— 用户可能从没有过这种资产，
     * 而退还不该因为「没有账户」失败。
     *
     * <p>⚠️ {@code bizRefId} 要和扣减时<b>不同</b>（比如加个 {@code :REFUND} 后缀），
     * 否则会被那条唯一键当成重复提交挡掉，表现是「退款静默不生效」。
     */
    @PostExchange("/refund")
    AssetDebitResult refund(@RequestBody AssetDebitCmd cmd);
}
