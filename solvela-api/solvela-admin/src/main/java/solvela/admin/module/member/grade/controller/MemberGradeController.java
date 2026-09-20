package solvela.admin.module.member.grade.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import solvela.admin.module.member.grade.domain.form.MemberGrowthLogQueryForm;
import solvela.admin.module.member.grade.domain.form.MemberGrowthQueryForm;
import solvela.admin.module.member.grade.domain.form.MemberGradeAdjustForm;
import solvela.admin.module.member.grade.domain.form.MemberGradeConfigForm;
import solvela.admin.module.member.grade.domain.form.MemberGradeLogQueryForm;
import solvela.base.domain.PageResult;
import solvela.base.util.SolvelaBeanUtil;
import solvela.enums.EnableStatusEnum;
import solvela.member.MemberGrade;
import solvela.member.grade.domain.dto.MemberGrowthDTO;
import solvela.member.grade.domain.dto.MemberGrowthLogDTO;
import solvela.member.grade.domain.dto.MemberGradeLogDTO;
import solvela.member.grade.domain.query.MemberGrowthLogQuery;
import solvela.member.grade.domain.query.MemberGrowthQuery;
import solvela.member.grade.domain.query.MemberGradeLogQuery;
import solvela.member.grade.service.MemberGradeAdminService;
import solvela.member.grade.service.MemberGradeConfigService;
import solvela.admin.auth.CurrentEmployee;
import solvela.web.RequiresPermission;

import java.util.List;

/**
 * 会员等级 Controller：等级配置、会员成长值、流水、留痕、人工调级。
 *
 * <h3>为什么直接下发 DTO，没有再套一层 VO</h3>
 * 这几个 DTO 已经是端无关的形状，且<b>不含任何 PII</b>（只有账号和昵称，没有手机号）。
 * 再抄一份字段完全一致的 VO，唯一的效果是将来加字段要改两个地方 ——
 * 而漏改的那一次会表现为「接口返回了但页面上没有」。
 *
 * <p>⚠️ 真要出现「管理端要显示、领域侧不该知道」的字段时，再加 VO 不迟。
 *
 * @author alaric
 * @date 2026-09-18
 */
@RequiredArgsConstructor
@RestController
@Tag(name = "会员等级")
@RequestMapping("/memberGrade")
public class MemberGradeController {

    private final MemberGradeAdminService memberGradeAdminService;
    private final MemberGradeConfigService memberGradeConfigService;

    // ==================== 等级配置 ====================

    @Operation(summary = "等级配置-全部（含停用），按等级升序 @author alaric")
    @GetMapping("/config/list")
    @RequiresPermission("memberGrade:query")
    public List<MemberGrade> listConfig() {
        return memberGradeConfigService.listAll();
    }

    @Operation(summary = "等级配置-新增/编辑 @author alaric")
    @PostMapping("/config/save")
    @RequiresPermission("memberGrade:config")
    public void saveConfig(@RequestBody @Valid MemberGradeConfigForm form) {
        memberGradeConfigService.save(SolvelaBeanUtil.copy(form, MemberGrade.class),
                CurrentEmployee.nameOrNull());
    }

    @Operation(summary = "等级配置-启用/停用 @author alaric")
    @GetMapping("/config/updateStatus")
    @RequiresPermission("memberGrade:config")
    public void updateConfigStatus(@RequestParam Long id, @RequestParam EnableStatusEnum status) {
        memberGradeConfigService.updateStatus(id, status, CurrentEmployee.nameOrNull());
    }

    // ==================== 会员成长值 ====================

    @Operation(summary = "会员成长值-分页 @author alaric")
    @PostMapping("/growth/queryPage")
    @RequiresPermission("memberGrade:query")
    public PageResult<MemberGrowthDTO> queryGrowthPage(@RequestBody @Valid MemberGrowthQueryForm form) {
        return memberGradeAdminService.queryGrowthPage(SolvelaBeanUtil.copy(form, MemberGrowthQuery.class));
    }

    /**
     * 单个会员的成长值现状。
     *
     * <p>⚠️ 从没拿到过成长值的会员返回 {@code null} —— 那是「还没参与过」，
     * 不是「查不到这个人」。前端要把这两种情况说成不同的话。
     */
    @Operation(summary = "会员成长值-详情。无记录返回 null @author alaric")
    @GetMapping("/growth/{memberId}")
    @RequiresPermission("memberGrade:query")
    public MemberGrowthDTO getGrowth(@PathVariable Long memberId) {
        return memberGradeAdminService.getGrowth(memberId);
    }

    @Operation(summary = "成长值流水-分页。memberId 必填 @author alaric")
    @PostMapping("/growthLog/queryPage")
    @RequiresPermission("memberGrade:query")
    public PageResult<MemberGrowthLogDTO> queryGrowthLogPage(@RequestBody @Valid MemberGrowthLogQueryForm form) {
        return memberGradeAdminService.queryGrowthLogPage(
                SolvelaBeanUtil.copy(form, MemberGrowthLogQuery.class));
    }

    @Operation(summary = "等级变更留痕-分页 @author alaric")
    @PostMapping("/gradeLog/queryPage")
    @RequiresPermission("memberGrade:query")
    public PageResult<MemberGradeLogDTO> queryGradeLogPage(@RequestBody @Valid MemberGradeLogQueryForm form) {
        return memberGradeAdminService.queryGradeLogPage(
                SolvelaBeanUtil.copy(form, MemberGradeLogQuery.class));
    }

    /**
     * 人工调级。
     *
     * <p>🔴 单独一个权限点 {@code memberGrade:adjust}，不跟配置共用 ——
     * 「能改等级规则」和「能改某一个人的等级」是两种完全不同的授权，
     * 后者是能被拿来给自己人送权益的那一种。
     */
    @Operation(summary = "人工调级。原因必填 @author alaric")
    @PostMapping("/adjust")
    @RequiresPermission("memberGrade:adjust")
    public void adjustGrade(@RequestBody @Valid MemberGradeAdjustForm form) {
        memberGradeAdminService.adjustGrade(form.getMemberId(), form.getNewGrade(),
                form.getReason(), CurrentEmployee.nameOrNull());
    }
}
