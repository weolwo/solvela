package solvela.risk.proposal.api;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import solvela.member.api.ProposalRecordApi;
import solvela.member.api.ProposalRecordView;
import solvela.risk.ProposalRecord;
import solvela.risk.proposal.manager.ProposalRecordManager;

import java.util.List;

/**
 * {@link ProposalRecordApi} 的实现：C 端「优惠记录」那一页的数据来源。
 *
 * <h3>只读，而且必须只读</h3>
 * 本类和 {@link ProposalApiService} 落在同一个包里，但两者的暴露面完全不同：
 * 那个能<b>造一笔发放</b>（走审批、预算、风控），只在服务端内部调；
 * 这个只查自己的记录，会被挂到网关上。
 * <b>不要因为它们挨得近就把方法加错地方。</b>
 *
 * <h3>🔴 会员号是查询条件，不是过滤条件</h3>
 * {@code member_id} 直接进 WHERE，走 {@code idx_prop_member(member_id, create_time)}。
 * 「查出来再在内存里筛自己的」那种写法在这里是灾难：提案表是全站发放流水，
 * 一次全表扫是分分钟的事，而且一旦有人漏了那个 filter，就是把别人的记录发给用户。
 */
@Service
@RequiredArgsConstructor
public class ProposalRecordApiService implements ProposalRecordApi {

    /** 一次最多回多少条。挡住调用方传一个巨大的 limit 把库拖垮 */
    private static final int MAX_LIMIT = 100;

    private final ProposalRecordManager proposalRecordManager;

    @Override
    public List<ProposalRecordView> listRecent(Long memberId, int limit) {
        if (memberId == null) {
            // 未登录不该走到这里（网关要登录态），真到了就给空列表而不是全站数据
            return List.of();
        }
        int size = Math.min(Math.max(limit, 1), MAX_LIMIT);
        return proposalRecordManager.lambdaQuery()
                .eq(ProposalRecord::getMemberId, memberId)
                .orderByDesc(ProposalRecord::getCreateTime)
                // 同一毫秒落的多条按 id 兜底，保证顺序稳定
                .orderByDesc(ProposalRecord::getId)
                .last("LIMIT " + size)
                .list().stream()
                .map(ProposalRecordApiService::toView)
                .toList();
    }

    private static ProposalRecordView toView(ProposalRecord record) {
        return new ProposalRecordView(
                record.getId(),
                record.getAssetType(),
                record.getAssetRef(),
                record.getAssetName(),
                record.getAmount(),
                record.getStatus(),
                record.getRemark(),
                record.getCreateTime());
    }
}
