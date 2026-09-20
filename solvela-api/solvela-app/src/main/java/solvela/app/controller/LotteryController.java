package solvela.app.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import solvela.app.auth.Anonymous;
import solvela.app.auth.CurrentMember;
import solvela.marketing.api.LotteryObtainResult;
import solvela.marketing.api.LotteryBoardView;
import solvela.marketing.api.ActivityDrawCmd;
import solvela.marketing.api.ActivityApi;
import solvela.marketing.api.LotteryIssueView;
import solvela.marketing.api.LotteryTicketView;

import java.util.List;
import java.util.UUID;

/**
 * 彩票：本期什么时候开奖、我手里有哪些号、中了没有。
 *
 * <h3>🔴 这条链路此前整个不存在</h3>
 * FPE 算号引擎、期号、号码池、中奖规则、管理端配置页、开奖与核销全都建成了，
 * 而 C 端一个控制器都没有 —— 会员既拿不到号（{@code PrizeTypeEnum.LOTTERY}
 * 的派发策略是空的，本次一并补上），也看不到自己有什么。
 *
 * <h3>没有「买一张」接口，这是设计</h3>
 * 彩票号码<b>只能从奖品派发拿到</b>：中奖 → {@code LotteryPrizeHandler} → 发一个号。
 * 要做「花积分买一张」得先回答一串产品问题（多少分一张、单人限购、卖不完怎么办），
 * 而领号引擎那一侧刻意没有资产扣减（它的类注释写着「消耗多少积分由上游算完再调进来」）。
 * <b>那是一个产品决策，不是补个端点的事</b> —— 与任务中心没有 claim 接口同一条规矩。
 *
 * @author alaric
 * @date 2026-09-18
 */
@Tag(name = "彩票")
@RestController
@RequestMapping("/lottery")
@RequiredArgsConstructor
public class LotteryController {

    /** 我的号码一次给多少。一个人手里几十张已经算多 */
    private static final int MY_TICKET_LIMIT = 100;

    private final ActivityApi activityApi;

    /**
     * 某个玩法的当前一期。
     *
     * <h3>匿名可看 —— 和活动列表同一个理由</h3>
     * 「本期几点截止、几点开奖」是活动的<b>公开面</b>，没登录也该看得到，
     * 否则分享出去的链接对新用户就是一堵墙。
     *
     * <p>登录了才会带上「我这期有几张」——{@link CurrentMember#memberIdOrNull()}
     * 拿不到就传 null，下游按 0 处理。
     *
     * <p>⚠️ 玩法不存在或已下线时返回 {@code null}，端上按「这个玩法没有了」处理；
     * 玩法在但没有在售期时返回的 view 里 {@code issueNo} 是 null ——
     * <b>这两件事不一样</b>，后者是正常的运营空窗，该说「下一期敬请期待」。
     */
    @Anonymous
    @Operation(summary = "彩票当前期号（匿名可看）")
    @GetMapping("/{lotteryCode}/issue")
    public LotteryIssueView getIssue(@PathVariable String lotteryCode) {
        return activityApi.getLotteryIssue(lotteryCode, CurrentMember.memberIdOrNull());
    }

    /**
     * 彩票活动页要的全部数据，一次给完。
     *
     * <h3>匿名可看 —— 和活动列表、抽奖活动页同一个理由</h3>
     * 活动页是分享出去的入口，要求先登录才能看一眼等于把分享链路掐断。
     * <b>领号那一步再要求登录</b>（见 {@link #obtain}）。
     *
     * <p>登录了才会带上「我这期的号码」那一块。
     */
    @Anonymous
    @Operation(summary = "彩票活动页数据（匿名可看）")
    @GetMapping("/board/{activityCode}")
    public LotteryBoardView getBoard(@PathVariable String activityCode) {
        return activityApi.getLotteryBoard(activityCode, CurrentMember.memberIdOrNull());
    }

    /**
     * 参与彩票活动：领一个号码。
     *
     * <h3>🔴 memberId 从登录态取，绝不接受客户端传</h3>
     * 领到的号码是能中奖的 —— 让客户端传就是「替别人领号」，
     * 而且开奖后奖品会发到那个人账上。
     *
     * <h3>requestId 由网关生成，不由客户端给</h3>
     * 它是领号引擎的幂等键。让客户端传的话，一个固定值就能让这个人<b>永远领不到号</b>
     * （每次都被判成重复提交），而反过来每次换一个新值则等于关掉了防重。
     * 网关这一侧一次请求一个新值，防的是<b>网络重试</b>，那正是它该防的。
     */
    @Operation(summary = "参与彩票活动，领一个号码")
    @PostMapping("/{activityCode}/obtain")
    public LotteryObtainResult obtain(@PathVariable String activityCode) {
        return activityApi.obtainLotteryTicket(new ActivityDrawCmd(
                activityCode, CurrentMember.require().memberId(),
                UUID.randomUUID().toString(), 1, null));
    }

    /**
     * 我的彩票号码，跨玩法跨期一起给。
     *
     * <p>排序是<b>中奖的在最前</b>，不是最新的在最前 ——
     * 用户点进来最想知道的是「我中了没有」。这个口径由服务端的 SQL 保证。
     */
    @Operation(summary = "我的彩票号码（中奖的排在最前）")
    @GetMapping("/ticket/mine")
    public List<LotteryTicketView> listMyTickets() {
        return activityApi.getMyLotteryTickets(CurrentMember.require().memberId(), MY_TICKET_LIMIT);
    }
}
