package solvela.admin.module.notification.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import solvela.admin.auth.CurrentEmployee;
import solvela.admin.module.notification.domain.form.ManualNotifyRequest;
import solvela.exception.BusinessException;
import solvela.member.service.MemberService;
import solvela.notification.domain.command.ManualNotifyCommand;
import solvela.notification.domain.command.ManualNotifyResult;
import solvela.notification.service.NotificationAdminService;
import solvela.web.RequiresPermission;

import java.util.ArrayList;
import java.util.List;

/**
 * 人工发送站内信 Controller。
 *
 * <h3>它补的缺口</h3>
 * 管理端此前只有两个入口：公告（广播给所有人）与模板（定义系统触发的措辞）。
 * <b>没有「发给某个人」</b> —— 客服想给一个会员补一句说明，只能去改库。
 *
 * <h3>公告和模板为什么本来就没关联</h3>
 * 顺带说清楚这件常被问到的事：公告<b>不用模板</b>。模板化解决的是
 * 「同一段文字存 N 遍」的冗余，而公告只有一行，没有冗余可省，
 * 套一层模板只会凭空多一层间接。三者的关系是：
 *
 * <ul>
 *   <li><b>模板</b> —— 系统自动触发的通知长什么样（中奖、发货…）；</li>
 *   <li><b>公告</b> —— 运营手写、广播给人群，一条内容一行；</li>
 *   <li><b>人工发送</b>（本类）—— 运营手写或套模板，<b>定向</b>给几个人，一人一条。</li>
 * </ul>
 *
 * <h3>🔴 收件人有硬上限，超了直接拒绝</h3>
 * 见 {@code NotificationAdminService.MANUAL_MAX_RECIPIENTS}。不是性能考虑，
 * 是怕这个入口变成「用写扩散做广播」的后门 —— 那正是整套设计最想避免的事，
 * 而且它还绕过了公告的人群规则与免打扰。
 *
 * @Author alaric
 * @Date 2026-09-15
 */
@RequiredArgsConstructor
@RestController
@Tag(name = "人工发送站内信")
@RequestMapping("/manualNotify")
public class ManualNotifyController {

    private final NotificationAdminService notificationAdminService;
    private final MemberService memberService;

    /**
     * 发送。收件人可以填会员号，也可以填会员账号 —— 客服手上通常只有后者。
     *
     * <p>⚠️ 账号查不到的会<b>整批拒绝</b>，不是跳过：运营贴了 5 个账号、
     * 其中一个打错字，静默发给 4 个人会让他以为 5 个都收到了。
     */
    @Operation(summary = "人工发一条站内信给指定会员（收件人有硬上限）")
    @PostMapping("/send")
    @RequiresPermission("manualNotify:send")
    public ManualNotifyResult send(@RequestBody @Valid ManualNotifyRequest request) {
        ManualNotifyCommand cmd = new ManualNotifyCommand();
        cmd.setMemberIds(resolveRecipients(request));
        cmd.setTemplateCode(request.getTemplateCode());
        cmd.setParams(request.getParams());
        cmd.setBizRefId(request.getBizRefId());

        try {
            return notificationAdminService.sendManual(cmd, CurrentEmployee.nameOrNull());
        } catch (IllegalArgumentException e) {
            // 领域层用 IllegalArgumentException 表达「参数不对」，在端上翻成业务异常，
            // 否则前端收到的是 500 而不是那句写得很清楚的提示
            throw new BusinessException(e.getMessage());
        }
    }

    /**
     * 会员号 + 会员账号合并成一份收件人名单。
     *
     * <p>账号 → 会员号的换算走 {@code MemberService} —— <b>不要在通知域里查会员表</b>，
     * 那个模块排在全部业务域之前、刻意不认识会员域。
     */
    private List<Long> resolveRecipients(ManualNotifyRequest request) {
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
            // 整批拒绝而不是跳过 —— 静默少发给一个人，运营不会发现
            throw new BusinessException("这些会员账号不存在：" + String.join("、", notFound));
        }
        if (ids.isEmpty()) {
            throw new BusinessException("收件人不能为空");
        }
        return ids;
    }
}
