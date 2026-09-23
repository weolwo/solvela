package solvela.admin.module.member.entitlement.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import solvela.admin.auth.CurrentEmployee;
import solvela.admin.module.member.entitlement.domain.form.GradeEntitlementForm;
import solvela.base.util.SolvelaBeanUtil;
import solvela.enums.EnableStatusEnum;
import solvela.member.GradeEntitlement;
import solvela.member.entitlement.service.GradeEntitlementAdminService;
import solvela.web.RequiresPermission;

import java.util.List;

/**
 * 等级权益配置：生日礼 / 月度券。
 *
 * <h3>🔴 权限点是单独的，不跟「等级权益展示」共用</h3>
 * {@code memberGrade:config} 改的是<b>页面上写什么</b>（{@code t_grade_privilege}）；
 * 这里改的是<b>真的发什么出去</b>。在这里加一条「每月给所有白金发一张 20 元券」，
 * 下一个 job 周期就会真的发 —— <b>能改一句文案和能承诺一笔预算，是两种授权</b>。
 *
 * @author alaric
 * @date 2026-09-22
 */
@RequiredArgsConstructor
@RestController
@Tag(name = "等级权益配置")
@RequestMapping("/memberEntitlement")
public class MemberEntitlementController {

    private final GradeEntitlementAdminService gradeEntitlementAdminService;

    @Operation(summary = "权益配置-全部（含停用） @author alaric")
    @GetMapping("/list")
    @RequiresPermission("memberEntitlement:query")
    public List<GradeEntitlement> list() {
        return gradeEntitlementAdminService.listAll();
    }

    /**
     * 新增 / 编辑。
     *
     * <p>⚠️ 编辑时<b>类型与编码会被服务端忽略</b>（沿用原值）。理由见
     * {@code GradeEntitlementForm#entitlementType} 上那段红字：改类型等于改幂等键的口径，
     * 会让同一个周期再发一次。
     */
    @Operation(summary = "权益配置-新增/编辑。编辑时类型不可改 @author alaric")
    @PostMapping("/save")
    @RequiresPermission("memberEntitlement:config")
    public void save(@RequestBody @Valid GradeEntitlementForm form) {
        gradeEntitlementAdminService.save(SolvelaBeanUtil.copy(form, GradeEntitlement.class),
                CurrentEmployee.nameOrNull());
    }

    /**
     * 启用 / 停用。
     *
     * <p>⚠️ 停用<b>不影响已经生成的待领取记录</b> —— 那些是已经承诺给用户的东西。
     * 停用只是不再生成新的。
     */
    @Operation(summary = "权益配置-启用/停用。不影响已生成的待领取 @author alaric")
    @GetMapping("/updateStatus")
    @RequiresPermission("memberEntitlement:config")
    public void updateStatus(@RequestParam Long id, @RequestParam EnableStatusEnum status) {
        gradeEntitlementAdminService.updateStatus(id, status, CurrentEmployee.nameOrNull());
    }
}
