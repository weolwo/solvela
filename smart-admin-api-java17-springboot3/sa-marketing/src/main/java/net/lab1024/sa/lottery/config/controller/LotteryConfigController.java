package net.lab1024.sa.lottery.config.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import net.lab1024.sa.base.common.domain.PageResult;
import net.lab1024.sa.base.common.domain.ResponseDTO;
import net.lab1024.sa.base.common.domain.ValidateList;
import net.lab1024.sa.lottery.config.domain.form.FpePreviewForm;
import net.lab1024.sa.lottery.config.domain.form.LotteryConfigAddForm;
import net.lab1024.sa.lottery.config.domain.form.LotteryConfigQueryForm;
import net.lab1024.sa.lottery.config.domain.form.LotteryConfigUpdateForm;
import net.lab1024.sa.lottery.config.domain.form.LotteryWorkbenchSaveForm;
import net.lab1024.sa.lottery.config.domain.vo.LotteryConfigOptionVO;
import net.lab1024.sa.lottery.config.domain.vo.LotteryConfigVO;
import net.lab1024.sa.lottery.config.domain.vo.LotteryWorkbenchVO;
import net.lab1024.sa.lottery.config.service.LotteryConfigService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 彩票配置 Controller
 *
 * @Author weolwo
 * @Date 2026-04-19 11:16:39
 * @Copyright weolwo
 */
@RequiredArgsConstructor
@RestController
@Tag(name = "彩票配置")
@RequestMapping("/lotteryConfig")
public class LotteryConfigController {

    private final LotteryConfigService Service;

    @Operation(summary = "分页查询")
    @PostMapping("/queryPage")
    @SaCheckPermission(":query")
    public ResponseDTO<PageResult<LotteryConfigVO>> queryPage(@RequestBody @Valid LotteryConfigQueryForm queryForm) {
        return ResponseDTO.ok(Service.queryPage(queryForm));
    }

    // ==================== 彩票工作台 ====================

    @Operation(summary = "生成彩票编码：10位大写字母+数字，服务端已判重")
    @GetMapping("/generateCode")
    @SaCheckPermission(":query")
    public ResponseDTO<String> generateCode() {
        return Service.generateCode();
    }

    @Operation(summary = "玩法下拉：按活动过滤，工作台顶部二级切换用")
    @GetMapping("/optionList")
    @SaCheckPermission(":query")
    public ResponseDTO<List<LotteryConfigOptionVO>> optionList(@RequestParam String activityCode) {
        return Service.optionList(activityCode);
    }

    @Operation(summary = "工作台聚合回显：lotteryCode 为空表示在该活动下新建玩法，返回带预生成编码的空壳")
    @GetMapping("/workbench/detail")
    @SaCheckPermission(":query")
    public ResponseDTO<LotteryWorkbenchVO> workbenchDetail(@RequestParam String activityCode,
                                                           @RequestParam(required = false) String lotteryCode) {
        return Service.workbenchDetail(activityCode, lotteryCode);
    }

    @Operation(summary = "工作台聚合保存：彩票配置 + 奖级规则，单事务")
    @PostMapping("/workbench/save")
    @SaCheckPermission(":update")
    public ResponseDTO<String> workbenchSave(@RequestBody @Valid LotteryWorkbenchSaveForm form) {
        return Service.workbenchSave(form);
    }

    @Operation(summary = "FPE 算号推演：用固定演示期号算样例号码，与线上发号同一段代码")
    @PostMapping("/fpe/preview")
    @SaCheckPermission(":query")
    public ResponseDTO<String> fpePreview(@RequestBody @Valid FpePreviewForm form) {
        return Service.fpePreview(form);
    }

    @Operation(summary = "添加")
    @PostMapping("/add")
    @SaCheckPermission(":add")
    public ResponseDTO<String> add(@RequestBody @Valid LotteryConfigAddForm addForm) {
        return Service.add(addForm);
    }

    @Operation(summary = "更新")
    @PostMapping("/update")
    @SaCheckPermission(":update")
    public ResponseDTO<String> update(@RequestBody @Valid LotteryConfigUpdateForm updateForm) {
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
