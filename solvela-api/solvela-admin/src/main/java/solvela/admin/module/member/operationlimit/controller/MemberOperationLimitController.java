package solvela.admin.module.member.operationlimit.controller;

import solvela.web.RequiresPermission;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import solvela.web.ResponseDTO;
import solvela.admin.auth.CurrentEmployee;
import solvela.member.operationlimit.constant.MemberOperationTypeEnum;
import solvela.member.operationlimit.constant.MemberOperationUnlockTypeEnum;
import solvela.member.MemberOperationLimit;
import solvela.admin.module.member.operationlimit.domain.form.MemberOperationUnlockForm;
import solvela.member.operationlimit.service.MemberOperationLimitService;

import java.util.List;

/**
 * 会员操作限制：客服查询与人工解冻。
 *
 * <h3>⚠️ 这里解的不是「封号」</h3>
 * 会员说「我被冻结了」时有两种可能，客服要先分清：
 * <ul>
 *   <li><b>账号被封</b> —— {@code t_member.status = FROZEN}，走 {@code /member/updateStatus}；</li>
 *   <li><b>某个功能被限</b> —— 本接口，风控自动触发、本来也会自动到期。</li>
 * </ul>
 * 解错了的表现是「解完还是进不去」，工单会二次流转。
 *
 * <h3>为什么解冻不需要「限制id」</h3>
 * 客服面对的是「这个人现在登录不了」，不是「第 12345 号限制记录」。
 * 按 (会员, 操作类型) 解，语义正好是「把他现在的这个限制解掉」，
 * 客服不用先查列表再挑一行 —— 少一步就少一次挑错行。
 *
 * <h3>为什么解冻是 POST 而不是 GET</h3>
 * 解冻要填原因（写进 remark），带 body 就只能 POST。顺带也更对：
 * GET 会被浏览器预取、被日志和 referer 完整记录，而这是个有副作用的写操作。
 *
 * @Date 2026-08-26
 */
@RequiredArgsConstructor
@RestController
@Tag(name = "会员操作限制")
@RequestMapping("/member/operationLimit")
public class MemberOperationLimitController {

    private static final int RECENT_LIMIT = 50;

    private final MemberOperationLimitService memberOperationLimitService;

    @Operation(summary = "查询某会员的限制记录（含历史，最近50条）")
    @GetMapping("/listByMember/{memberId}")
    @RequiresPermission("member:query")
    public ResponseDTO<List<MemberOperationLimit>> listByMember(@PathVariable Long memberId) {
        return ResponseDTO.ok(memberOperationLimitService.listRecentByMember(memberId, RECENT_LIMIT));
    }

    /**
     * 人工解冻。幂等：当前没有生效中的限制时返回提示而非报错 ——
     * 客服点第二次、或刚好在自动到期后点，都不该看到一个红色的失败。
     */
    @Operation(summary = "人工解冻 @author 客服")
    @PostMapping("/unlock")
    @RequiresPermission("member:update")
    public ResponseDTO<String> unlock(@RequestBody @Valid MemberOperationUnlockForm form) {
        MemberOperationTypeEnum type = MemberOperationTypeEnum.resolve(form.getOperationType());
        if (type == null) {
            return ResponseDTO.userErrorParam("不支持的操作类型");
        }
        // 操作人落到 operator 列：这一列存在的唯一理由就是事后能追到人，
        // 取当前登录员工而不是让前端传，避免被改
        String operator = CurrentEmployee.nameOrNull();
        boolean unlocked = memberOperationLimitService.unlock(
                form.getMemberId(), type, MemberOperationUnlockTypeEnum.MANUAL, operator, form.getRemark());
        return unlocked ? ResponseDTO.ok("已解冻") : ResponseDTO.ok("该会员当前没有生效中的限制");
    }
}
