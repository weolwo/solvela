package solvela.member.api;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;

import java.util.List;

/**
 * 资产（钱包）的对外契约。实现在 {@code solvela-ledger}。
 *
 * <h3>为什么在 member-api 而不是一个新的 ledger-api</h3>
 * 本模块的 pom 写着「粒度对齐<b>服务</b>、不对齐今天的 maven 模块：会员在 solvela-member、
 * 资产在 solvela-ledger、提案与优惠配置在 solvela-risk，三个实现模块共用这一份契约。
 * <b>所以不会再有 solvela-ledger-api</b>」。资产将来和会员同属 app-member 那个服务，
 * 契约就该在一起 —— 真拆进程的那天，换的是网关侧那一个 bean，不是这里。
 *
 * <h3>🔴 这里只读，不写</h3>
 * 加钱减钱一律走 {@code MemberProposalApi.createProposal}（提案带审批、预算与风控），
 * 或商城那条自己的兑换事务。<b>永远不要在这个接口上加 deduct / charge</b> ——
 * 那等于给资产开一个绕过闸门的旁路，而且是从公网入口那一侧开的。
 * {@code LedgerBoundaryTest} 守的就是这条缝。
 *
 * <h3>路径前缀 /internal 是有意的</h3>
 * 服务于服务间调用，不该直接暴露到公网：公网入口是网关自己的 {@code /assets}，
 * 鉴权、限流、字段裁剪都在那一层。
 */
@HttpExchange("/internal/asset")
public interface AssetApi {

    /**
     * 某个会员的全部资产。
     *
     * <p>没有钱包记录时返回<b>空列表，不是 null</b> —— 新注册、还没拿过任何奖励的用户
     * 就是这个状态，它是正常的，不该让调用方去判空。
     *
     * <p>⚠️ 会员号由<b>调用方从登录态取</b>并放进路径。网关那边用的是
     * {@code CurrentMember.require().memberId()}，客户端传的一律不认 ——
     * 否则就是「查别人的余额」。
     */
    @GetExchange("/{memberId}")
    List<MemberAssetView> listAssets(@PathVariable Long memberId);
}
