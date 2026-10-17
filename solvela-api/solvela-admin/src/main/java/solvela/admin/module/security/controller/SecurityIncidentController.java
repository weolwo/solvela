package solvela.admin.module.security.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import solvela.base.domain.PageResult;
import solvela.member.risk.security.SecurityIncidentAdminService;
import solvela.web.RequiresPermission;

import java.time.LocalDateTime;

/**
 * 安全中心 - 安全事件（网关方案 §6.2、§6.4）。
 *
 * <p>🔴 {@code matchedSample}、{@code userAgent} 原样返回，是攻击者可控的内容 —— 前端只按纯文本渲染。
 *
 * @Date 2026-10-17
 */
@RequiredArgsConstructor
@RestController
@Tag(name = "安全中心 - 安全事件")
@RequestMapping("/security/incident")
public class SecurityIncidentController {

    private final SecurityIncidentAdminService service;

    public record QueryForm(String clientIp, String deviceId, Long memberId, Integer incidentType,
                            LocalDateTime createTimeBegin, LocalDateTime createTimeEnd, Long pageNum, Long pageSize) {
    }

    @Operation(summary = "安全事件分页。不传时间兜最近 7 天，一次最多 31 天")
    @PostMapping("/queryPage")
    @RequiresPermission("security:query")
    public PageResult<SecurityIncidentAdminService.Row> queryPage(@RequestBody QueryForm form) {
        return service.queryPage(new SecurityIncidentAdminService.Query(form.clientIp(), form.deviceId(),
                form.memberId(), form.incidentType(), form.createTimeBegin(), form.createTimeEnd(),
                form.pageNum() == null ? 1 : form.pageNum(), form.pageSize() == null ? 20 : form.pageSize()));
    }
}
