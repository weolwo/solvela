package solvela.admin.module.security.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import solvela.admin.auth.CurrentEmployee;
import solvela.member.risk.security.SecurityGateAdminService;
import solvela.web.RequiresPermission;

/**
 * 安全中心 - 蜜罐与关键字（网关方案 §5.3、§5.4、§6.2）：增、删、单独停用，看熔断状态。
 * 关键字规则保存前要过反例集（后台存不进去的，网关也不会加载）。
 *
 * @Date 2026-10-17
 */
@RequiredArgsConstructor
@RestController
@Tag(name = "安全中心 - 蜜罐与关键字")
@RequestMapping("/security/gate")
public class SecurityGateController {

    private final SecurityGateAdminService service;

    public record EnableForm(@NotNull SecurityGateAdminService.Kind kind, @NotBlank String code,
                             @NotNull Boolean enabled) {
    }

    public record HoneypotForm(@NotBlank @Size(max = 32) String code, @NotBlank @Size(max = 200) String path) {
    }

    public record KeywordForm(@NotBlank @Size(max = 32) String code, @NotBlank String scope,
                              @NotBlank @Size(max = 500) String pattern) {
    }

    public record RemoveForm(@NotNull SecurityGateAdminService.Kind kind, @NotBlank String code) {
    }

    @Operation(summary = "蜜罐路径、关键字规则与熔断状态")
    @PostMapping("/get")
    @RequiresPermission("security:query")
    public SecurityGateAdminService.Gate get() {
        return service.get();
    }

    @Operation(summary = "单独启用 / 停用一条 @author 风控")
    @PostMapping("/setEnabled")
    @RequiresPermission("security:rule")
    public String setEnabled(@RequestBody @Valid EnableForm form) {
        service.setEnabled(form.kind(), form.code(), form.enabled(), CurrentEmployee.nameOrNull());
        return "已保存，15 秒内各网关实例生效";
    }

    @Operation(summary = "加一条蜜罐路径 @author 风控")
    @PostMapping("/addHoneypot")
    @RequiresPermission("security:rule")
    public String addHoneypot(@RequestBody @Valid HoneypotForm form) {
        service.addHoneypot(form.code(), form.path(), CurrentEmployee.nameOrNull());
        return "已新增，15 秒内各网关实例生效（与真实接口重叠的不会启用）";
    }

    @Operation(summary = "加一条关键字规则（要过反例集） @author 风控")
    @PostMapping("/addKeyword")
    @RequiresPermission("security:rule")
    public String addKeyword(@RequestBody @Valid KeywordForm form) {
        service.addKeyword(form.code(), form.scope(), form.pattern(), CurrentEmployee.nameOrNull());
        return "已新增，15 秒内各网关实例生效";
    }

    @Operation(summary = "删一条 @author 风控")
    @PostMapping("/remove")
    @RequiresPermission("security:rule")
    public String remove(@RequestBody @Valid RemoveForm form) {
        service.remove(form.kind(), form.code(), CurrentEmployee.nameOrNull());
        return "已删除";
    }
}
