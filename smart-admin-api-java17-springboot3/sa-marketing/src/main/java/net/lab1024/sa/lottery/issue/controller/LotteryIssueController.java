package net.lab1024.sa.lottery.issue.controller;

import net.lab1024.sa.base.common.domain.ValidateList;
import net.lab1024.sa.lottery.issue.domain.entity.LotteryIssue;
import net.lab1024.sa.lottery.issue.domain.form.LotteryIssueAddForm;
import net.lab1024.sa.lottery.issue.domain.form.LotteryIssueQueryForm;
import net.lab1024.sa.lottery.issue.domain.form.LotteryIssueUpdateForm;
import net.lab1024.sa.lottery.issue.domain.vo.LotteryIssueVO;
import net.lab1024.sa.lottery.issue.service.LotteryIssueService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import net.lab1024.sa.base.common.domain.ResponseDTO;
import net.lab1024.sa.base.common.domain.PageResult;
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

    @Operation(summary = "批量删除")
    @PostMapping("/batchDelete")
    @SaCheckPermission("lotteryIssue:delete")
    public ResponseDTO<String> batchDelete(@RequestBody ValidateList<Long> idList) {
        return Service.batchDelete(idList);
    }

    @Operation(summary = "单个删除")
    @GetMapping("/delete/{id}")
    @SaCheckPermission("lotteryIssue:delete")
    public ResponseDTO<String> batchDelete(@PathVariable Long id) {
        return Service.delete(id);
    }
}
