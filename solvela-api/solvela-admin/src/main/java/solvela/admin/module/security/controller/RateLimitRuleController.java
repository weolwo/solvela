package solvela.admin.module.security.controller;

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
import solvela.admin.auth.CurrentEmployee;
import solvela.member.RateLimitRule;
import solvela.member.risk.security.RateLimitRuleAdminService;
import solvela.web.RequiresPermission;

import java.util.List;

/**
 * 安全中心 - 限流规则（网关方案 §6.1、§6.2）。保存即重发网关快照，页头看各实例已加载的版本。
 *
 * @Date 2026-10-17
 */
@RequiredArgsConstructor
@RestController
@Tag(name = "安全中心 - 限流规则")
@RequestMapping("/security/rateLimitRule")
public class RateLimitRuleController {

    private final RateLimitRuleAdminService service;

    /** 整页：规则、网关上报的路由（接口覆盖的下拉框与失效标红）、版本 */
    public record Page(List<RateLimitRule> rules, List<RateLimitRuleAdminService.Route> routes,
                       RateLimitRuleAdminService.Versions versions) {
    }

    /** 一行的阈值与档位 */
    public record Values(@NotNull Integer burst, @NotNull Integer limit3m, @NotNull Integer limit5m,
                         @NotNull Integer limit10m, String longWindow, Integer limitLong, @NotNull Integer mode) {

        RateLimitRuleAdminService.RuleValues toValues() {
            return new RateLimitRuleAdminService.RuleValues(burst, limit3m, limit5m, limit10m, longWindow, limitLong, mode);
        }
    }

    public record UpdateForm(@NotNull Long id, @NotNull @Valid Values values) {
    }

    public record AddEndpointForm(@NotBlank String endpointKey, @NotNull Integer dimension,
                                  @NotNull @Valid Values values) {
    }

    public record IdForm(@NotNull Long id) {
    }

    @Operation(summary = "限流规则、路由清单与各实例已加载版本")
    @PostMapping("/page")
    @RequiresPermission("security:query")
    public Page page() {
        return new Page(service.list(), service.routes(), service.versions());
    }

    @Operation(summary = "改一行的阈值与档位 @author 风控")
    @PostMapping("/update")
    @RequiresPermission("security:rule")
    public String update(@RequestBody @Valid UpdateForm form) {
        service.update(form.id(), form.values().toValues(), CurrentEmployee.nameOrNull());
        return "已保存，15 秒内各网关实例生效";
    }

    @Operation(summary = "加一条按接口的覆盖 @author 风控")
    @PostMapping("/addEndpoint")
    @RequiresPermission("security:rule")
    public String addEndpoint(@RequestBody @Valid AddEndpointForm form) {
        service.addEndpoint(form.endpointKey(), form.dimension(), form.values().toValues(), CurrentEmployee.nameOrNull());
        return "已新增，15 秒内各网关实例生效";
    }

    @Operation(summary = "删一条接口覆盖（场景行不能删） @author 风控")
    @PostMapping("/deleteEndpoint")
    @RequiresPermission("security:rule")
    public String deleteEndpoint(@RequestBody @Valid IdForm form) {
        service.deleteEndpoint(form.id(), CurrentEmployee.nameOrNull());
        return "已删除";
    }
}
