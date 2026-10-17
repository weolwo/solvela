package solvela.admin.module.security.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import solvela.admin.auth.CurrentEmployee;
import solvela.member.risk.security.PenaltyAdminService;
import solvela.web.RequiresPermission;

import java.util.List;

/**
 * 安全中心 - 处罚查询与解除（网关方案 §6.2）。按 IP / 设备 / 会员号精确查（直接 GET Redis 键，不扫库）。
 *
 * <p>🔴 解除也要留下是谁解的：一个被解掉的蜜罐处罚，下一次它再来时，「上次为什么放了」只能靠这条日志。
 *
 * @Date 2026-10-17
 */
@RequiredArgsConstructor
@RestController
@Tag(name = "安全中心 - 处罚")
@RequestMapping("/security/penalty")
public class PenaltyController {

    private final PenaltyAdminService service;

    public record QueryForm(String ip, String deviceId, Long memberId) {
    }

    public record ReleaseForm(@NotBlank String key) {
    }

    @Operation(summary = "查此刻在罚的东西")
    @PostMapping("/query")
    @RequiresPermission("security:query")
    public List<PenaltyAdminService.Penalty> query(@RequestBody QueryForm form) {
        if (isBlank(form.ip()) && isBlank(form.deviceId()) && form.memberId() == null) {
            throw new IllegalArgumentException("IP、设备号、会员号至少填一个");
        }
        return service.query(form.ip(), form.deviceId(), form.memberId());
    }

    @Operation(summary = "解除一条处罚 @author 风控")
    @PostMapping("/release")
    @RequiresPermission("security:penalty")
    public String release(@RequestBody @Valid ReleaseForm form) {
        return service.release(form.key(), CurrentEmployee.nameOrNull())
                ? "已解除，几秒内各网关实例生效"
                : "这条处罚已经不在了";
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
