package net.lab1024.sa.draw.drawlog.controller;

import net.lab1024.sa.base.common.domain.ValidateList;
import net.lab1024.sa.draw.drawlog.domain.entity.DrawPrizeLog;
import net.lab1024.sa.draw.drawlog.domain.form.DrawPrizeLogAddForm;
import net.lab1024.sa.draw.drawlog.domain.form.DrawPrizeLogQueryForm;
import net.lab1024.sa.draw.drawlog.domain.form.DrawPrizeLogUpdateForm;
import net.lab1024.sa.draw.drawlog.domain.vo.DrawPrizeLogVO;
import net.lab1024.sa.draw.drawlog.service.DrawPrizeLogService;
import net.lab1024.sa.draw.runtime.DrawExecuteService;
import net.lab1024.sa.draw.runtime.domain.DrawExecuteForm;
import net.lab1024.sa.draw.runtime.domain.DrawExecuteVO;
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
 * 抽奖记录 Controller
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

    @Operation(summary = "添加")
    @PostMapping("/add")
    @SaCheckPermission("drawPrizeLog:add")
    public ResponseDTO<String> add(@RequestBody @Valid DrawPrizeLogAddForm addForm) {
        return Service.add(addForm);
    }

    @Operation(summary = "更新")
    @PostMapping("/update")
    @SaCheckPermission("drawPrizeLog:update")
    public ResponseDTO<String> update(@RequestBody @Valid DrawPrizeLogUpdateForm updateForm) {
        return Service.update(updateForm);
    }

    @Operation(summary = "批量删除")
    @PostMapping("/batchDelete")
    @SaCheckPermission("drawPrizeLog:delete")
    public ResponseDTO<String> batchDelete(@RequestBody ValidateList<Long> idList) {
        return Service.batchDelete(idList);
    }

    @Operation(summary = "单个删除")
    @GetMapping("/delete/{id}")
    @SaCheckPermission("drawPrizeLog:delete")
    public ResponseDTO<String> batchDelete(@PathVariable Long id) {
        return Service.delete(id);
    }
}
