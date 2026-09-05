package solvela.biz.server.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RestController;
import solvela.member.api.ProposalRecordApi;
import solvela.member.api.ProposalRecordView;
import solvela.risk.proposal.api.ProposalRecordApiService;

import java.util.List;

/**
 * {@link ProposalRecordApi} 的 HTTP 薄壳 —— C 端「优惠记录」那一页。
 *
 * <p>照 {@link MemberAuthInternalController} 的形状：implements 接口，
 * 路径与方法只在契约里定义一次。
 *
 * <h3>🔴 这里<b>没有</b> MemberProposalApi 的壳，是刻意的</h3>
 * 那个接口有 {@code createProposal} —— 发钱的闸门。给它开一个 HTTP 入口，
 * 等于让「造一笔发放」变成一次网络调用可达的事。
 * 它至今只在进程内被调用，就让它保持这样。
 *
 * <p>⚠️ 按实现类注入而不是按接口：本进程里 {@code ProposalRecordApi} 类型的 bean
 * 有两个（本类与 {@link ProposalRecordApiService}），按接口注入会歧义。
 * 与 {@code MallInternalController} 同一个理由。
 */
@RestController
@RequiredArgsConstructor
public class ProposalRecordInternalController implements ProposalRecordApi {

    private final ProposalRecordApiService proposalRecordApiService;

    @Override
    public List<ProposalRecordView> listRecent(Long memberId, int limit) {
        return proposalRecordApiService.listRecent(memberId, limit);
    }
}
