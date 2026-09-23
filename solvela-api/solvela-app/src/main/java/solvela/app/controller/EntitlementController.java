package solvela.app.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import solvela.app.auth.CurrentMember;
import solvela.member.api.EntitlementClaimResult;
import solvela.member.api.MemberEntitlementApi;
import solvela.member.api.MemberEntitlementView;

import java.util.List;

/**
 * 我的等级权益：生日礼、月度券。
 *
 * <h3>🔴 memberId 一律从登录态取，绝不接受客户端传</h3>
 * 列表那边接受客户端传等于「查任意人有什么权益」；
 * 领取那边更糟 —— 改一个参数就能<b>领走别人的东西</b>。
 * 域侧的条件更新里也带着 memberId，那是第二道，不是唯一一道。
 *
 * <h3>为什么只有「领」没有「发」</h3>
 * 发是 job 的事，按等级与周期生成待领取记录。C 端给一个发的口子，
 * 等于把权益发放的判据交给客户端 —— 那是这个域存在理由的反面。
 *
 * @author alaric
 * @date 2026-09-22
 */
@Tag(name = "我的权益")
@RestController
@RequestMapping("/entitlement")
@RequiredArgsConstructor
public class EntitlementController {

    /** 默认给多少条。服务端还有一道 100 的硬上限 */
    private static final int DEFAULT_LIMIT = 30;

    private final MemberEntitlementApi memberEntitlementApi;

    /**
     * 我的权益列表，待领取的在前、快过期的靠前。
     *
     * <p>⚠️ 已领取与已过期的<b>也返回</b>：只给待领取的话，用户点完领取就什么都
     * 看不见了，会以为「刚才那个东西没了」。
     */
    @Operation(summary = "我的权益：待领取在前，已领取与已过期也一并返回")
    @GetMapping
    public List<MemberEntitlementView> mine() {
        return memberEntitlementApi.mine(CurrentMember.require().memberId(), DEFAULT_LIMIT);
    }

    /**
     * 领取一份权益。
     *
     * <p>🔴 领不了时抛业务异常（已领过 / 过期了 / 不是他的），
     * 而不是返回一个「失败」的成功响应 —— 后者会让端上以为自己要自己判。
     */
    @Operation(summary = "领取一份待领取的权益，返回领到的东西叫什么")
    @PostMapping("/{grantId}/claim")
    public EntitlementClaimResult claim(@PathVariable Long grantId) {
        return memberEntitlementApi.claim(CurrentMember.require().memberId(), grantId);
    }
}
