package net.lab1024.sa.prize.prizelog.controller;

import net.lab1024.sa.base.common.domain.ValidateList;
import net.lab1024.sa.prize.prizelog.domain.entity.PrizeLog;
import net.lab1024.sa.prize.prizelog.domain.form.PrizeLogAddForm;
import net.lab1024.sa.prize.prizelog.domain.form.PrizeLogQueryForm;
import net.lab1024.sa.prize.prizelog.domain.form.PrizeLogUpdateForm;
import net.lab1024.sa.prize.prizelog.domain.vo.PrizeLogVO;
import net.lab1024.sa.base.common.util.SmartRequestUtil;
import net.lab1024.sa.consumer.handler.PrizeDispatchHandler;
import net.lab1024.sa.prize.prizelog.service.PrizeLogService;
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
    @SaCheckPermission("prizeLog:approve")
    public ResponseDTO<String> approveDispatch(@PathVariable Long id) {
        return prizeDispatchHandler.approveDispatch(id, SmartRequestUtil.getRequestUser().getUserName());
    }

    @Operation(summary = "发奖审批驳回")
    @GetMapping("/reject/{id}")
    @SaCheckPermission("prizeLog:approve")
    public ResponseDTO<String> rejectDispatch(@PathVariable Long id, @RequestParam(required = false) String reason) {
        return prizeDispatchHandler.rejectDispatch(id, SmartRequestUtil.getRequestUser().getUserName(), reason);
    }

    @Operation(summary = "分页查询")
    @PostMapping("/queryPage")
    @SaCheckPermission("prizeLog:query")
    public ResponseDTO<PageResult<PrizeLogVO>> queryPage(@RequestBody @Valid PrizeLogQueryForm queryForm) {
        return ResponseDTO.ok(Service.queryPage(queryForm));
    }

    @Operation(summary = "添加")
    @PostMapping("/add")
    @SaCheckPermission("prizeLog:add")
    public ResponseDTO<String> add(@RequestBody @Valid PrizeLogAddForm addForm) {
        return Service.add(addForm);
    }

    @Operation(summary = "更新")
    @PostMapping("/update")
    @SaCheckPermission("prizeLog:update")
    public ResponseDTO<String> update(@RequestBody @Valid PrizeLogUpdateForm updateForm) {
        return Service.update(updateForm);
    }

    @Operation(summary = "批量删除")
    @PostMapping("/batchDelete")
    @SaCheckPermission("prizeLog:delete")
    public ResponseDTO<String> batchDelete(@RequestBody ValidateList<Long> idList) {
        return Service.batchDelete(idList);
    }

    @Operation(summary = "单个删除")
    @GetMapping("/delete/{id}")
    @SaCheckPermission("prizeLog:delete")
    public ResponseDTO<String> batchDelete(@PathVariable Long id) {
        return Service.delete(id);
    }
}
