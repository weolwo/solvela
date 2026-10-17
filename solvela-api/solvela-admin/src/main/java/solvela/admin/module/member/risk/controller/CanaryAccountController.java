package solvela.admin.module.member.risk.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import solvela.admin.auth.CurrentEmployee;
import solvela.admin.module.system.datatracer.constant.DataTracerTypeEnum;
import solvela.admin.module.system.datatracer.service.DataTracerService;
import solvela.member.risk.security.CanaryAccountService;
import solvela.web.RequiresPermission;

/**
 * 蜜罐账号（网关方案 §5.1）。建几个、放在哪由运营定；🔴 邮箱不进代码与种子数据，只能从这里建。
 *
 * <p>🔴 <b>必须留痕</b>：谁、什么时候建了一个蜜罐 —— 它日后命中时，「这个邮箱当初放在哪了」只能靠这条记录往回找。
 * 留痕里<b>不写邮箱</b>：留痕表谁都能翻，蜜罐邮箱从我们这边流出去，这个蜜罐就废了。
 *
 * @Date 2026-10-17
 */
@RequiredArgsConstructor
@RestController
@Tag(name = "登录风控 - 蜜罐账号")
@RequestMapping("/memberRisk/canary")
public class CanaryAccountController {

    private final CanaryAccountService canaryAccountService;

    private final DataTracerService dataTracerService;

    @Operation(summary = "用一个从没被用过的邮箱建蜜罐账号 @author 风控")
    @PostMapping("/create")
    @RequiresPermission("memberRisk:list")
    public String create(@RequestBody @Valid CreateForm form) {
        long memberId = canaryAccountService.create(form.email(), CurrentEmployee.nameOrNull());
        dataTracerService.addTrace(memberId, DataTracerTypeEnum.SECURITY_LIST,
                "新建蜜罐账号，投放位置：" + form.placement());
        return "已建，会员号 " + memberId;
    }

    /**
     * @param placement 投放位置（放在哪个「可能泄露」的地方）—— 日后命中时，要靠它判断是哪里漏的
     */
    public record CreateForm(
            @NotBlank(message = "邮箱不能为空") @Size(max = 128) String email,
            @NotBlank(message = "请写明投放位置：日后命中时，要靠它判断是哪里漏的") @Size(max = 100) String placement) {
    }
}
