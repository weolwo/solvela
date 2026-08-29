package solvela.admin.module.lottery.issue.controller;

import solvela.base.domain.ValidateList;
import solvela.lottery.LotteryIssue;
import solvela.admin.module.lottery.issue.domain.form.LotteryIssueAddForm;
import solvela.lottery.issue.domain.command.LotteryIssueAddCommand;
import solvela.admin.module.lottery.issue.domain.form.LotteryIssueQueryForm;
import solvela.lottery.issue.domain.query.LotteryIssueQuery;
import solvela.admin.module.lottery.issue.domain.form.LotteryIssueUpdateForm;
import solvela.lottery.issue.domain.command.LotteryIssueUpdateCommand;
import solvela.lottery.issue.domain.dto.LotteryIssueOverviewDTO;
import solvela.admin.module.lottery.issue.domain.vo.LotteryIssueVO;
import solvela.lottery.issue.domain.dto.LotteryIssueDTO;
import solvela.lottery.issue.service.LotteryIssueService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import solvela.base.util.SolvelaBeanUtil;
import solvela.base.dao.SolvelaPageUtil;
import solvela.base.domain.PageResult;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import solvela.web.RequiresPermission;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * 期号配置 Controller
 *
 * @Author weolwo
 * @Date 2026-05-09 16:54:51
 * @Copyright weolwo
 */
@RequiredArgsConstructor
@RestController
@Tag(name = "期号配置")
@RequestMapping("/lotteryIssue")
public class LotteryIssueController {

    private final LotteryIssueService Service;

    @Operation(summary = "分页查询")
    @PostMapping("/queryPage")
    @RequiresPermission("lotteryIssue:query")
    public PageResult<LotteryIssueVO> queryPage(@RequestBody @Valid LotteryIssueQueryForm queryForm) {
        PageResult<LotteryIssueDTO> page = Service.queryPage(SolvelaBeanUtil.copy(queryForm, LotteryIssueQuery.class));
        return SolvelaPageUtil.convert2PageResult(page, LotteryIssueVO.class);
    }

    @Operation(summary = "巡检概览：逾期未开奖/售卖中/已售罄/今日计划开奖。只吃 lotteryCode 一个条件")
    @PostMapping("/overview")
    @RequiresPermission("lotteryIssue:query")
    public LotteryIssueOverviewDTO overview(@RequestBody @Valid LotteryIssueQueryForm queryForm) {
        return Service.overview(SolvelaBeanUtil.copy(queryForm, LotteryIssueQuery.class));
    }

    @Operation(summary = "添加")
    @PostMapping("/add")
    @RequiresPermission("lotteryIssue:add")
    public void add(@RequestBody @Valid LotteryIssueAddForm addForm) {
        Service.add(SolvelaBeanUtil.copy(addForm, LotteryIssueAddCommand.class));
    }

    @Operation(summary = "更新")
    @PostMapping("/update")
    @RequiresPermission("lotteryIssue:update")
    public void update(@RequestBody @Valid LotteryIssueUpdateForm updateForm) {
        Service.update(SolvelaBeanUtil.copy(updateForm, LotteryIssueUpdateCommand.class));
    }

    @Operation(summary = "停售：把售卖结束时间提前到此刻，立刻停止发号。想恢复售卖走编辑改回未来时刻")
    @GetMapping("/stopSale/{id}")
    @RequiresPermission("lotteryIssue:update")
    public void stopSale(@PathVariable Long id) {
        Service.stopSale(id);
    }

    @Operation(summary = "批量停售：逐期停售并回一句汇总，已停售/已开奖计入跳过")
    @PostMapping("/batchStopSale")
    @RequiresPermission("lotteryIssue:update")
    public String batchStopSale(@RequestBody ValidateList<Long> idList) {
        return Service.batchStopSale(idList);
    }

    @Operation(summary = "单个删除：只能删「零发号 + 待开奖」的空期，供工作台清理误建的期号")
    @GetMapping("/delete/{id}")
    @RequiresPermission("lotteryIssue:delete")
    public void batchDelete(@PathVariable Long id) {
        Service.delete(id);
    }
}
