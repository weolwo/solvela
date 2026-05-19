package net.lab1024.sa.lottery.numberpool.controller;

import net.lab1024.sa.base.common.domain.ValidateList;
import net.lab1024.sa.lottery.numberpool.domain.entity.LotteryNumberPool;
import net.lab1024.sa.lottery.numberpool.domain.form.LotteryNumberPoolAddForm;
import net.lab1024.sa.lottery.numberpool.domain.form.LotteryNumberPoolQueryForm;
import net.lab1024.sa.lottery.numberpool.domain.form.LotteryNumberPoolUpdateForm;
import net.lab1024.sa.lottery.numberpool.domain.vo.LotteryNumberPoolVO;
import net.lab1024.sa.lottery.numberpool.service.LotteryNumberPoolService;
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
 * 彩票号码池 Controller
 *
 * @Author weolwo
 * @Date 2026-04-19 11:31:09
 * @Copyright weolwo
 */
@RequiredArgsConstructor
@RestController
@Tag(name = "彩票号码池")
@RequestMapping("/lotteryNumberPool")
public class LotteryNumberPoolController {

    private final LotteryNumberPoolService Service;

    @Operation(summary = "分页查询")
    @PostMapping("/queryPage")
    @SaCheckPermission(":query")
    public ResponseDTO<PageResult<LotteryNumberPoolVO>> queryPage(@RequestBody @Valid LotteryNumberPoolQueryForm queryForm) {
        return ResponseDTO.ok(Service.queryPage(queryForm));
    }

    @Operation(summary = "添加")
    @PostMapping("/add")
    @SaCheckPermission(":addProposal")
    public ResponseDTO<String> add(@RequestBody @Valid LotteryNumberPoolAddForm addForm) {
        return Service.add(addForm);
    }

    @Operation(summary = "更新")
    @PostMapping("/update")
    @SaCheckPermission(":update")
    public ResponseDTO<String> update(@RequestBody @Valid LotteryNumberPoolUpdateForm updateForm) {
        return Service.update(updateForm);
    }

    @Operation(summary = "批量删除")
    @PostMapping("/batchDelete")
    @SaCheckPermission(":delete")
    public ResponseDTO<String> batchDelete(@RequestBody ValidateList<Long> idList) {
        return Service.batchDelete(idList);
    }

    @Operation(summary = "单个删除")
    @GetMapping("/delete/{id}")
    @SaCheckPermission(":delete")
    public ResponseDTO<String> batchDelete(@PathVariable Long id) {
        return Service.delete(id);
    }
}
