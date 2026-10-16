package solvela.admin.module.member.risk.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import solvela.admin.module.member.risk.domain.form.SecurityListAddForm;
import solvela.admin.module.member.risk.domain.form.SecurityListQueryForm;
import solvela.admin.module.member.risk.domain.vo.SecurityListVO;
import solvela.admin.module.system.datatracer.constant.DataTracerTypeEnum;
import solvela.admin.module.system.datatracer.service.DataTracerService;
import solvela.admin.auth.CurrentEmployee;
import solvela.base.dao.SolvelaPageUtil;
import solvela.base.domain.PageResult;
import solvela.base.util.SolvelaBeanUtil;
import solvela.base.util.SolvelaStringUtil;
import solvela.crypto.PiiHasher;
import solvela.enums.SecurityListDimensionEnum;
import solvela.member.SecurityList;
import solvela.member.risk.domain.SecurityListAddCmd;
import solvela.member.risk.domain.query.SecurityListQuery;
import solvela.member.risk.service.MemberRiskAdminService;
import solvela.member.util.MemberEmailUtil;
import solvela.member.util.MemberPhoneUtil;
import solvela.web.RequiresPermission;

/**
 * 登录风控 - 名单（黑 / 灰 / 白）。
 *
 * <h3>🔴 豁免面刻意很窄</h3>
 * 白名单上的东西恰恰是攻击者最想拿到的，所以：IP 白名单只跳过 IP 维度的计数与限流，
 * 会员白名单只跳过矩阵与打分 —— 全局滑块、密码冷却、设备封禁一个都不豁免（方案 §5.2）。
 * 这一点页面上要写出来，否则客服会以为「加了白名单就万事大吉」。
 *
 * <h3>🔴 撤销不删行</h3>
 * 「谁加的、谁解的、为什么」都要查得到。删行的话，下一个人看到的是
 * 「这个 IP 从来没被封过」—— 而他正要判断「上次是不是误封」。
 *
 * <h3>权限分成两个</h3>
 * {@code memberRisk:query} 客服日常要看；{@code memberRisk:list} 才能增删 ——
 * 加一条 IP 黑名单可能把一整个公司出口挡在外面，这不是查流水那一级的权力。
 *
 * @Date 2026-10-16
 */
@RequiredArgsConstructor
@RestController
@Tag(name = "登录风控 - 名单")
@RequestMapping("/memberRiskList")
public class SecurityListController {

    private final MemberRiskAdminService riskAdminService;

    private final DataTracerService dataTracerService;

    private final PiiHasher piiHasher;

    @Operation(summary = "分页查询。不传状态只看有效的")
    @PostMapping("/queryPage")
    @RequiresPermission("memberRisk:query")
    public PageResult<SecurityListVO> queryPage(@RequestBody @Valid SecurityListQueryForm form) {
        SecurityListQuery query = SolvelaBeanUtil.copy(form, SecurityListQuery.class);
        if (SolvelaStringUtil.isNotBlank(form.getIdentity())) {
            query.setListValue(hashIdentity(form.getIdentity()));
        }
        PageResult<SecurityList> page = riskAdminService.queryListPage(query);
        return SolvelaPageUtil.convert2PageResult(page, SecurityListVO.class);
    }

    /**
     * 新增。
     *
     * <p>🔴 <b>必须留痕</b>：一条 IP 黑名单可能把一整个出口挡在外面，
     * 而「上周是谁加的、为什么」是那时唯一能回答问题的东西。
     */
    @Operation(summary = "新增一条名单 @author 风控")
    @PostMapping("/add")
    @RequiresPermission("memberRisk:list")
    public String add(@RequestBody @Valid SecurityListAddForm form) {
        SecurityListAddCmd cmd = SolvelaBeanUtil.copy(form, SecurityListAddCmd.class);
        /*
         * 身份维度：页面上输的是明文，库里存的是摘要。
         * 规范化再算 hash —— 不规范化的话「138 0000 0000」和「13800000000」是两行，
         * 而登录那一侧只认后者算出来的那一个（同决策流水页）。
         */
        if (form.getDimension() == SecurityListDimensionEnum.IDENTITY) {
            cmd.setListValue(hashIdentity(form.getIdentity()));
        }
        cmd.setOperator(CurrentEmployee.nameOrNull());
        SecurityList row = riskAdminService.addList(cmd);
        dataTracerService.addTrace(row.getId(), DataTracerTypeEnum.SECURITY_LIST,
                "新增 %s（%s）：%s，到期 %s，原因：%s".formatted(
                        row.getListType().getDesc(), row.getDimension().getDesc(),
                        row.getListValue(), row.getExpireTime(), row.getReason()));
        return "已新增";
    }

    /**
     * 撤销。幂等：本来就已撤销时返回提示而非报错 ——
     * 客服点第二次、或刚好在到期之后点，都不该看到一个红色的失败。
     */
    @Operation(summary = "撤销一条名单 @author 风控")
    @PostMapping("/revoke")
    @RequiresPermission("memberRisk:list")
    public String revoke(@RequestBody @Valid RevokeForm form) {
        boolean revoked = riskAdminService.revokeList(
                form.id(), CurrentEmployee.nameOrNull(), form.reason());
        if (!revoked) {
            return "该名单已不在有效状态";
        }
        dataTracerService.addTrace(form.id(), DataTracerTypeEnum.SECURITY_LIST,
                "撤销，原因：" + form.reason());
        return "已撤销";
    }

    /** 撤销的入参。原因必填 —— 「上次为什么解的」和「为什么加的」一样重要 */
    public record RevokeForm(
            @NotNull(message = "id 不能为空") Long id,
            @NotBlank(message = "请写明撤销原因") String reason) {
    }

    /**
     * 明文 → 摘要。
     *
     * <p>🔴 必须先规范化：{@code "138 0000 0000"} 与 {@code "13800000000"} 算出来是不同的 hash，
     * 而登录那一侧是规范化之后算的 —— 不规范化就永远匹配不上，而页面只会显示「没有数据」。
     * 与决策流水页同一个做法（两处都调这段逻辑，取舍一致）。
     */
    private String hashIdentity(String identity) {
        if (SolvelaStringUtil.isBlank(identity)) {
            return null;
        }
        String normalized = MemberPhoneUtil.normalize(identity);
        if (normalized == null) {
            normalized = MemberEmailUtil.normalize(identity);
        }
        return piiHasher.hash(normalized == null ? identity.trim() : normalized);
    }
}
