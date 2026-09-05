package solvela.member.api;

import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;

import java.util.List;

/**
 * 提案记录的<b>只读</b>查询：C 端「优惠记录」那一页。实现在 {@code solvela-risk}。
 *
 * <h3>🔴 为什么不加在 MemberProposalApi 上</h3>
 * 那个接口有 {@code createProposal} —— <b>发钱的闸门</b>（带审批、预算、风控）。
 * 而这一页要接到<b>网关</b>上，网关是公网入口。
 * 把读和写放在同一个接口里，等于为了查一页记录而让公网入口拿到「造一笔发放」的能力。
 *
 * <p>这就是 {@link AssetApi}（只读，挂网关）与 {@link AssetDebitApi}（能扣钱，不挂网关）
 * 分成两个接口的同一条理由。<b>永远不要在这个接口上加写方法。</b>
 *
 * <h3>「优惠记录」是提案记录的对外说法</h3>
 * {@code t_proposal_record} 记的是「平台要发给你什么」——
 * 活动中奖、任务达标、人工补发都会落一条。对用户就是「我得了什么优惠」。
 * 内部叫提案是因为它要过审批，那是运营视角；C 端不该出现这个词。
 */
@HttpExchange("/internal/member/proposal-record")
public interface ProposalRecordApi {

    /**
     * 我的优惠记录，按时间倒序。
     *
     * <p>只取 limit 条，<b>不分页</b> —— 与奖品记录同一个判断：
     * C 端这类「最近发生了什么」的列表，用户翻不到第三屏。
     * 真需要分页时再加，那时要连着一个游标，不是 pageNum
     *（按时间倒序的列表用 offset 分页，新数据进来会让第二页重复出现第一页的行）。
     */
    @GetExchange
    List<ProposalRecordView> listRecent(@RequestParam Long memberId, @RequestParam int limit);
}
