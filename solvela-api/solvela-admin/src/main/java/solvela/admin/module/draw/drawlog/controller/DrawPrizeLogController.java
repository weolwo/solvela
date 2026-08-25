package solvela.admin.module.draw.drawlog.controller;

import solvela.draw.drawlog.domain.form.DrawPrizeLogQueryForm;
import solvela.draw.drawlog.domain.vo.DrawFunnelVO;
import solvela.draw.drawlog.domain.vo.DrawPrizeLogVO;
import solvela.draw.drawlog.service.DrawPrizeLogService;
import solvela.draw.runtime.DrawExecuteService;
import solvela.draw.runtime.domain.DrawExecuteForm;
import solvela.draw.runtime.domain.DrawExecuteVO;
import solvela.base.common.domain.ResponseDTO;
import solvela.base.common.domain.PageResult;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import cn.dev33.satoken.annotation.SaCheckPermission;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * 抽奖记录 Controller —— 流水<b>只读</b>。
 *
 * <h3>⚠️ add / update / delete / batchDelete 已刻意移除，不要再加回来</h3>
 * 抽奖流水是发奖凭证与对账依据：用户说「我明明抽中了」、财务对「这个月发了多少奖」，
 * 依据都是这张表。后台能改能删，等于这套审计不存在。
 *
 * <p>而且这四个接口从来就没有正当用途 —— 流水由 {@link DrawExecuteService} 在抽奖链路里写入，
 * 没有任何场景需要人工补录或修改一条抽奖记录，它们只是生成器顺带产出的。
 *
 * <p>清理数据（如压测后重跑基线）走 DBA 脚本，不该从后台点：
 * 见「抽奖模块-联调造数.sql」的清场章节 —— 那里要求同时清 Redis 的库存与限领计数，
 * 只删流水表反而会留下更不一致的状态。
 *
 * <p>{@code /execute} 保留：它是抽奖的运行态入口，不是对流水的写操作。
 *
 * @Author weolwo
 * @Date 2026-04-19 09:21:26
 * @Copyright weolwo
 */
@RequiredArgsConstructor
@RestController
@Tag(name = "抽奖记录")
@RequestMapping("/drawPrizeLog")
public class DrawPrizeLogController {

    private final DrawPrizeLogService Service;
    private final DrawExecuteService drawExecuteService;

    @Operation(summary = "执行抽奖（引擎判定 + Lua预扣 + DB兜底 + 落流水）")
    @PostMapping("/execute")
    @SaCheckPermission("drawPrizeLog:execute")
    public ResponseDTO<DrawExecuteVO> execute(@RequestBody @Valid DrawExecuteForm executeForm) {
        return drawExecuteService.execute(executeForm);
    }

    @Operation(summary = "分页查询")
    @PostMapping("/queryPage")
    @SaCheckPermission("drawPrizeLog:query")
    public ResponseDTO<PageResult<DrawPrizeLogVO>> queryPage(@RequestBody @Valid DrawPrizeLogQueryForm queryForm) {
        return ResponseDTO.ok(Service.queryPage(queryForm));
    }

    @Operation(summary = "抽奖转化漏斗：中奖率、库存不足率、参与人数、奖品发放分布")
    @PostMapping("/funnel")
    @SaCheckPermission("drawPrizeLog:query")
    public ResponseDTO<DrawFunnelVO> funnel(@RequestBody @Valid DrawPrizeLogQueryForm queryForm) {
        return ResponseDTO.ok(Service.funnel(queryForm));
    }
}
