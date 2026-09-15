package solvela.admin.module.ledger.coupontemplate.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import solvela.admin.auth.CurrentEmployee;
import solvela.admin.module.ledger.coupontemplate.domain.ManualCouponRequest;
import solvela.exception.BusinessException;
import solvela.ledger.coupon.manual.CouponManualGrantService;
import solvela.ledger.coupon.manual.domain.ManualCouponGrantCmd;
import solvela.ledger.coupon.manual.domain.ManualCouponGrantResult;
import solvela.member.service.MemberService;
import solvela.web.RequiresPermission;

import java.util.ArrayList;
import java.util.List;

/**
 * 人工发券 Controller。
 *
 * <h3>它补的缺口</h3>
 * 券此前只能由<b>活动 / 任务 / 商城</b>发出来。客服想给一个会员补一张券，
 * 只能去改库 —— 而改库既没有规则快照，也没有通知，更没有留痕。
 *
 * <h3>🔴 权限点单独一个，不并进 couponTemplate:save</h3>
 * 配规则和<b>直接给用户发钱</b>是两件要分别授予的事。
 * 这和公告那边把「删除（含确认留痕）」单列出来是同一条道理。
 *
 * <h3>🔴 收件人有硬上限，超了直接拒绝</h3>
 * 见 {@code CouponManualGrantService.MAX_RECIPIENTS}。不是性能考虑，
 * 是怕这个入口变成「给全体用户发券」的后门 —— 绕过活动配置、绕过预算、绕过风控。
 *
 * @Author alaric
 * @Date 2026-09-15
 */
@RequiredArgsConstructor
@RestController
@Tag(name = "人工发券")
@RequestMapping("/manualCoupon")
public class ManualCouponController {

    private final CouponManualGrantService couponManualGrantService;
    private final MemberService memberService;

    /**
     * 发券。收件人可以填会员号，也可以填会员账号 —— 客服手上通常只有后者。
     *
     * <p>⚠️ 账号查不到的会<b>整批拒绝</b>，不是跳过：运营贴了 5 个账号、
     * 其中一个打错字，静默发给 4 个人会让他以为 5 个都收到了。
     */
    @Operation(summary = "人工发券给指定会员（收件人有硬上限，按工单号防重）")
    @PostMapping("/send")
    @RequiresPermission("manualCoupon:send")
    public ManualCouponGrantResult send(@RequestBody @Valid ManualCouponRequest request) {
        ManualCouponGrantCmd cmd = new ManualCouponGrantCmd(
                resolveRecipients(request),
                request.getCouponCode(),
                request.getQuantity(),
                request.getBizRefId(),
                request.getReason());
        return couponManualGrantService.grant(cmd, CurrentEmployee.nameOrNull());
    }

    /**
     * 会员号 + 会员账号合并成一份收件人名单。
     *
     * <p>与人工发站内信共用同一套规则：<b>有一个账号查不到就整批拒绝</b>。
     * 静默少发给一个人，运营不会发现 —— 而这里少发的是钱。
     */
    private List<Long> resolveRecipients(ManualCouponRequest request) {
        List<Long> ids = new ArrayList<>(
                request.getMemberIds() == null ? List.of() : request.getMemberIds());

        List<String> names = request.getMemberNames() == null ? List.of() : request.getMemberNames();
        List<String> notFound = new ArrayList<>();
        for (String name : names) {
            if (name == null || name.isBlank()) {
                continue;
            }
            Long memberId = memberService.getMemberId(name.trim());
            if (memberId == null) {
                notFound.add(name.trim());
            } else {
                ids.add(memberId);
            }
        }

        if (!notFound.isEmpty()) {
            throw new BusinessException("这些会员账号不存在：" + String.join("、", notFound));
        }
        if (ids.isEmpty()) {
            throw new BusinessException("收件人不能为空");
        }
        return ids;
    }
}
