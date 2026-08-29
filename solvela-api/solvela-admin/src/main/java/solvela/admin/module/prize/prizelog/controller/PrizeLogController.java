package solvela.admin.module.prize.prizelog.controller;

import solvela.web.RequiresPermission;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import solvela.base.dao.SolvelaPageUtil;
import solvela.base.domain.PageResult;
import solvela.base.util.SolvelaBeanUtil;
import solvela.admin.auth.CurrentEmployee;
import solvela.consumer.handler.PrizeDispatchHandler;
import solvela.admin.module.prize.prizelog.domain.form.PrizeLogAddForm;
import solvela.prize.prizelog.domain.command.PrizeLogAddCommand;
import solvela.admin.module.prize.prizelog.domain.form.PrizeLogQueryForm;
import solvela.prize.prizelog.domain.query.PrizeLogQuery;
import solvela.prize.prizelog.domain.dto.PrizeLogFunnelDTO;
import solvela.admin.module.prize.prizelog.domain.vo.PrizeLogVO;
import solvela.prize.prizelog.domain.dto.PrizeLogDTO;
import solvela.prize.prizelog.service.PrizeLogService;

/**
 * 奖励记录表 Controller
 *
 * @Author weolwo
 * @Date 2026-04-18 20:27:03
 * @Copyright weolwo
 */
@RequiredArgsConstructor
@RestController
@Tag(name = "奖励记录表")
@RequestMapping("/prizeLog")
public class PrizeLogController {

    private final PrizeLogService Service;

    private final PrizeDispatchHandler prizeDispatchHandler;

    @Operation(summary = "发奖审批通过（approve_mode=1 的奖品唯一出口，通过后立即派发）")
    @GetMapping("/approve/{id}")
    @RequiresPermission("prizeLog:approve")
    public void approveDispatch(@PathVariable Long id) {
        prizeDispatchHandler.approveDispatch(id, CurrentEmployee.nameOrNull());
    }

    @Operation(summary = "发奖审批驳回")
    @GetMapping("/reject/{id}")
    @RequiresPermission("prizeLog:approve")
    public void rejectDispatch(@PathVariable Long id, @RequestParam(required = false) String reason) {
        prizeDispatchHandler.rejectDispatch(id, CurrentEmployee.nameOrNull(), reason);
    }

    @Operation(summary = "分页查询")
    @PostMapping("/queryPage")
    @RequiresPermission("prizeLog:query")
    public PageResult<PrizeLogVO> queryPage(@RequestBody @Valid PrizeLogQueryForm queryForm) {
        PageResult<PrizeLogDTO> page = Service.queryPage(SolvelaBeanUtil.copy(queryForm, PrizeLogQuery.class));
        return SolvelaPageUtil.convert2PageResult(page, PrizeLogVO.class);
    }

    @Operation(summary = "奖励漏斗：已发出条数与价值（双口径）、审批积压、卡单、失败原因与一致性体检")
    @PostMapping("/funnel")
    @RequiresPermission("prizeLog:query")
    public PrizeLogFunnelDTO funnel(@RequestBody @Valid PrizeLogQueryForm queryForm) {
        return Service.funnel(SolvelaBeanUtil.copy(queryForm, PrizeLogQuery.class));
    }

    @Operation(summary = "添加")
    @PostMapping("/add")
    @RequiresPermission("prizeLog:add")
    public void add(@RequestBody @Valid PrizeLogAddForm addForm) {
        Service.add(SolvelaBeanUtil.copy(addForm, PrizeLogAddCommand.class));
    }

    /*
     * 生成器给的「更新 / 批量删除 / 单个删除」已移除（v3.69.0）：
     * t_prize_log 是"实际发了什么奖"的审计流水，改历史会让流水与真实发放脱节，
     * 而 delete 原先还是物理删除，删完连"少了什么"都查不出来。
     *
     * ⚠️ add 刻意保留：人工补发是<b>往前追加一条新流水</b>，不是改旧账，
     * 它本身就是一次真实发放的起点（落 approve_status=1 待审批，
     * 审批通过后才真正走提案与账务）。前端「人工补发」按钮用的就是它。
     */
}
