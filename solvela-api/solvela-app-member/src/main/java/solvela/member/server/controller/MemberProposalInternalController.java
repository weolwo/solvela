package solvela.member.server.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RestController;
import solvela.member.api.CreateProposalCmd;
import solvela.member.api.MemberProposalApi;
import solvela.member.api.ProposalResult;
import solvela.risk.proposal.api.ProposalApiService;

/**
 * {@link MemberProposalApi} 的 HTTP 薄壳：营销侧把一笔奖交过来。
 *
 * <h3>⚠️ 这个类被漏掉过一次，代价值得记</h3>
 * {@code ProposalApiService} 实现了接口，但它是 {@code @Service} ——
 * <b>Spring MVC 只给 {@code @Controller}/{@code @RestController} 建映射</b>。
 * 没有本类时，端点根本不存在，请求得到的是 404（被兜底处理器包成 500）。
 *
 * <p>而这件事<b>所有进程内的测试都发现不了</b>：会员服务的上下文照常启动、
 * 营销服务的上下文也照常启动（它那边只是个 HTTP 代理，不校验对端存在），
 * 一直要到第一次真实发奖才炸。端到端联调抓出它，靠的就是真发了一次 HTTP。
 *
 * <p>教训：<b>每加一个 api 接口，就要问一句「服务端的壳建了没有」</b>。
 * 契约实现类和 HTTP 薄壳是两件事。
 *
 * <h3>🔴 不能对公网开放</h3>
 * 本端点直接生成资产提案，入口层必须把 {@code /internal/**} 整体挡在外面。
 */
@RestController
@RequiredArgsConstructor
public class MemberProposalInternalController implements MemberProposalApi {

    /** 注入实现类而不是接口：本进程里 MemberProposalApi 有两个 bean（本类与它） */
    private final ProposalApiService proposalApiService;

    @Override
    public ProposalResult createProposal(CreateProposalCmd cmd) {
        return proposalApiService.createProposal(cmd);
    }
}
