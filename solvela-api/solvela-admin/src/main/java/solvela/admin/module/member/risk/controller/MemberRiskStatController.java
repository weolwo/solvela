package solvela.admin.module.member.risk.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import solvela.member.risk.domain.dto.MemberRiskStatDTO;
import solvela.member.risk.service.MemberRiskAdminService;
import solvela.web.RequiresPermission;

import java.time.LocalDateTime;

/**
 * 登录风控 - 看板。
 *
 * <h3>它回答的是「阈值该往哪边调」</h3>
 * 流水页回答「这个人为什么被拦」；而「要不要把某条规则切生效、全局滑块什么时候关掉」
 * 只能看聚合 —— 打扰了多少人、拦下了多少、生效与陪跑差了多少（方案 §8.2）。
 *
 * <h3>🔴 四组数一个接口返回</h3>
 * 它们共用同一个时间窗。拆成四个接口，前端就有机会用四个不同的窗去问，
 * 然后把对不上的数摆在同一个屏幕上 —— 而那种错没人查得出来。
 *
 * @Date 2026-10-16
 */
@RequiredArgsConstructor
@RestController
@Tag(name = "登录风控 - 看板")
@RequestMapping("/memberRiskStat")
public class MemberRiskStatController {

    private final MemberRiskAdminService riskAdminService;

    @Operation(summary = "看板：结论分布 / 矩阵各格 / 核身漏斗 / 规则命中。不传时间默认最近 30 天")
    @PostMapping("/stat")
    @RequiresPermission("memberRisk:query")
    public MemberRiskStatDTO stat(@RequestBody @Valid StatForm form) {
        return riskAdminService.stat(form.startTime(), form.endTime());
    }

    /**
     * 时间窗。两头都可空（兜最近 30 天），但一次最多 90 天 ——
     * 再长该导出离线算，聚合一个季度会把分区全扫一遍。
     */
    public record StatForm(LocalDateTime startTime, LocalDateTime endTime) {
    }
}
