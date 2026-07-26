package net.lab1024.sa.lottery.record.controller;

import net.lab1024.sa.lottery.record.domain.entity.LotteryRecord;
import net.lab1024.sa.lottery.record.domain.form.LotteryRecordAddForm;
import net.lab1024.sa.lottery.record.domain.form.LotteryRecordQueryForm;
import net.lab1024.sa.lottery.record.domain.form.LotteryRecordUpdateForm;
import net.lab1024.sa.lottery.record.domain.vo.LotteryRecordVO;
import net.lab1024.sa.lottery.record.service.LotteryRecordService;
import net.lab1024.sa.base.common.domain.ResponseDTO;
import net.lab1024.sa.base.common.domain.PageResult;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import cn.dev33.satoken.annotation.SaCheckPermission;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
/**
 * 用户号码记录 Controller
 *
 * @Author weolwo
 * @Date 2026-04-19 11:57:08
 * @Copyright weolwo
 */
@RequiredArgsConstructor
@RestController
@Tag(name = "用户号码记录")
@RequestMapping("/lotteryRecord")
public class LotteryRecordController {

    private final LotteryRecordService Service;

    @Operation(summary = "分页查询")
    @PostMapping("/queryPage")
    @SaCheckPermission(":query")
    public ResponseDTO<PageResult<LotteryRecordVO>> queryPage(@RequestBody @Valid LotteryRecordQueryForm queryForm) {
        return ResponseDTO.ok(Service.queryPage(queryForm));
    }

    @Operation(summary = "添加")
    @PostMapping("/add")
    @SaCheckPermission(":add")
    public ResponseDTO<String> add(@RequestBody @Valid LotteryRecordAddForm addForm) {
        return Service.add(addForm);
    }

    @Operation(summary = "更新")
    @PostMapping("/update")
    @SaCheckPermission(":update")
    public ResponseDTO<String> update(@RequestBody @Valid LotteryRecordUpdateForm updateForm) {
        return Service.update(updateForm);
    }

}
