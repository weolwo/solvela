package solvela.admin.module.member.risk.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import solvela.admin.module.member.risk.domain.form.MemberRiskRuleUpdateForm;
import solvela.admin.module.member.risk.domain.vo.MemberRiskRuleVO;
import solvela.admin.module.system.datatracer.constant.DataTracerTypeEnum;
import solvela.admin.module.system.datatracer.service.DataTracerService;
import solvela.base.util.SolvelaBeanUtil;
import solvela.enums.MemberRiskModeEnum;
import solvela.base.util.SolvelaEnumUtil;
import solvela.member.MemberRiskRule;
import solvela.member.risk.service.MemberRiskAdminService;
import solvela.web.RequiresPermission;

import java.util.List;

/**
 * 登录风控 - 规则。
 *
 * <h3>这个页面能改什么、不能改什么</h3>
 * 规则的<b>逻辑在代码里</b>（一条规则一个 Java 类），这里能调的只有「松紧」：
 * 档位（关闭 / 陪跑 / 生效）、分值、参数。编码、阶段、类型都是只读的 ——
 * 让人在页面上改它们等于在页面上改代码。
 *
 * <h3>🔴 全局滑块的开关就是这个页面上的一行</h3>
 * {@code GLOBAL_CAPTCHA} 的档位即它的开关：被攻击时把它切成「生效」，
 * 30 秒内所有密码登录都要过滑块。这是整套风控的应急档，所以这一行要在页面上置顶。
 *
 * @Date 2026-10-16
 */
@RequiredArgsConstructor
@RestController
@Tag(name = "登录风控 - 规则")
@RequestMapping("/memberRiskRule")
public class MemberRiskRuleController {

    private final MemberRiskAdminService riskAdminService;

    private final DataTracerService dataTracerService;

    @Operation(summary = "规则列表（前置在前、后置在后）")
    @PostMapping("/list")
    @RequiresPermission("security:query")
    public List<MemberRiskRuleVO> list() {
        return SolvelaBeanUtil.copyList(riskAdminService.listRules(), MemberRiskRuleVO.class);
    }

    /**
     * 改档位 / 分值 / 参数。
     *
     * <p>🔴 <b>一定要留痕</b>：切一次档位影响的是所有用户 —— 「上周为什么突然人人都要验证码」
     * 这个问题，没有变更记录就只能靠猜。改动走 {@code DataTracer}，与定时任务那套一致。
     */
    @Operation(summary = "改规则的档位 / 分值 / 参数")
    @PostMapping("/update")
    @RequiresPermission("security:rule")
    public MemberRiskRuleVO update(@RequestBody @Valid MemberRiskRuleUpdateForm form) {
        MemberRiskRule before = riskAdminService.getRule(form.getId());
        if (before == null) {
            throw new IllegalArgumentException("规则不存在");
        }
        MemberRiskRule after = riskAdminService.updateRule(form.getId(),
                SolvelaEnumUtil.getEnumByValue(form.getMode(), MemberRiskModeEnum.class), form.getScore(), form.getParams());

        dataTracerService.addTrace(form.getId(), DataTracerTypeEnum.MEMBER_RISK_RULE,
                describe(before, after));
        return SolvelaBeanUtil.copy(after, MemberRiskRuleVO.class);
    }

    /**
     * 留痕里写人话，不写两个 JSON。
     *
     * <p>「档位 陪跑 → 生效」一眼看得懂；贴两份完整参数的话，下一个人要自己逐字段比。
     */
    private static String describe(MemberRiskRule before, MemberRiskRule after) {
        StringBuilder sb = new StringBuilder(after.getRuleCode()).append(' ');
        if (before.getMode() != after.getMode()) {
            sb.append("档位 ").append(before.getMode().getDesc())
                    .append(" → ").append(after.getMode().getDesc()).append("；");
        }
        if (!java.util.Objects.equals(before.getScore(), after.getScore())) {
            sb.append("分值 ").append(before.getScore()).append(" → ").append(after.getScore()).append("；");
        }
        if (!java.util.Objects.equals(before.getParams(), after.getParams())) {
            sb.append("参数已修改；");
        }
        if (sb.length() == after.getRuleCode().length() + 1) {
            sb.append("没有实际变化");
        }
        return sb.toString();
    }
}
