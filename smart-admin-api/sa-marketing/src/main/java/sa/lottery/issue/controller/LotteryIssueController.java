package sa.lottery.issue.controller;

import sa.base.common.domain.ValidateList;
import sa.lottery.issue.domain.entity.LotteryIssue;
import sa.lottery.issue.domain.form.LotteryIssueAddForm;
import sa.lottery.issue.domain.form.LotteryIssueQueryForm;
import sa.lottery.issue.domain.form.LotteryIssueUpdateForm;
import sa.lottery.issue.domain.vo.LotteryIssueOverviewVO;
import sa.lottery.issue.domain.vo.LotteryIssueVO;
import sa.lottery.issue.service.LotteryIssueService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import sa.base.common.domain.ResponseDTO;
import sa.base.common.domain.PageResult;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import cn.dev33.satoken.annotation.SaCheckPermission;
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
    @SaCheckPermission("lotteryIssue:query")
    public ResponseDTO<PageResult<LotteryIssueVO>> queryPage(@RequestBody @Valid LotteryIssueQueryForm queryForm) {
        return ResponseDTO.ok(Service.queryPage(queryForm));
    }

    @Operation(summary = "巡检概览：逾期未开奖/售卖中/已售罄/今日计划开奖。只吃 lotteryCode 一个条件")
    @PostMapping("/overview")
    @SaCheckPermission("lotteryIssue:query")
    public ResponseDTO<LotteryIssueOverviewVO> overview(@RequestBody @Valid LotteryIssueQueryForm queryForm) {
        return ResponseDTO.ok(Service.overview(queryForm));
    }

    @Operation(summary = "添加")
    @PostMapping("/add")
    @SaCheckPermission("lotteryIssue:add")
    public ResponseDTO<String> add(@RequestBody @Valid LotteryIssueAddForm addForm) {
        return Service.add(addForm);
    }

    @Operation(summary = "更新")
    @PostMapping("/update")
    @SaCheckPermission("lotteryIssue:update")
    public ResponseDTO<String> update(@RequestBody @Valid LotteryIssueUpdateForm updateForm) {
        return Service.update(updateForm);
    }

    @Operation(summary = "停售：把售卖结束时间提前到此刻，立刻停止发号。想恢复售卖走编辑改回未来时刻")
    @GetMapping("/stopSale/{id}")
    @SaCheckPermission("lotteryIssue:update")
    public ResponseDTO<String> stopSale(@PathVariable Long id) {
        return Service.stopSale(id);
    }

    @Operation(summary = "批量停售：逐期停售并回一句汇总，已停售/已开奖计入跳过")
    @PostMapping("/batchStopSale")
    @SaCheckPermission("lotteryIssue:update")
    public ResponseDTO<String> batchStopSale(@RequestBody ValidateList<Long> idList) {
        return Service.batchStopSale(idList);
    }

    @Operation(summary = "单个删除：只能删「零发号 + 待开奖」的空期，供工作台清理误建的期号")
    @GetMapping("/delete/{id}")
    @SaCheckPermission("lotteryIssue:delete")
    public ResponseDTO<String> batchDelete(@PathVariable Long id) {
        return Service.delete(id);
    }
}
