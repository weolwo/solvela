package solvela.admin.module.lottery.config.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import solvela.base.dao.SolvelaPageUtil;
import solvela.base.domain.PageResult;
import solvela.base.domain.ResponseDTO;
import solvela.base.util.SolvelaBeanUtil;
import solvela.base.domain.ValidateList;
import solvela.lottery.config.domain.dto.LotteryConfigBoardResultDTO;
import solvela.lottery.config.service.LotteryConfigBoardService;
import solvela.admin.module.lottery.config.domain.form.FpePreviewForm;
import solvela.lottery.config.domain.command.FpePreviewCommand;
import solvela.admin.module.lottery.config.domain.form.LotteryConfigQueryForm;
import solvela.lottery.config.domain.query.LotteryConfigQuery;
import solvela.admin.module.lottery.config.domain.form.LotteryWorkbenchSaveForm;
import solvela.lottery.config.domain.command.LotteryWorkbenchSaveCommand;
import solvela.lottery.config.domain.dto.LotteryConfigOptionDTO;
import solvela.admin.module.lottery.config.domain.vo.LotteryConfigVO;
import solvela.lottery.config.domain.dto.LotteryConfigDTO;
import solvela.lottery.config.domain.dto.LotteryWorkbenchDTO;
import solvela.lottery.config.service.LotteryConfigService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 彩票配置 Controller
 *
 * <p>🔴 <b>没有 add / update / delete。</b> 一个玩法不是一张扁平表单能配出来的东西：
 * 它至少要同时落 {@code t_lottery_config} 与 {@code t_lottery_prize_rule}，
 * 而且号码长度、发行总量在发过号之后是<b>永久冻结</b>的（结构锁）。
 * 生成器产出的那套扁平增改绕开了这些校验 —— 配出来的玩法上不了线，改出来的玩法会把
 * 发号引擎参数改坏。所有写操作统一收口到工作台的
 * {@code /workbench/save}（配置 + 奖级，单事务）。
 *
 * <p>删除同理不给：{@code t_lottery_record} 里存着 lottery_code，
 * 删配置会让用户手里已发出的号码指向一条不存在的玩法，开奖与客诉自证全断。
 * 停售用 {@link #offline(String)} —— 立刻停止发号，已发出的号码不受影响，期号照常开奖。
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

    private final LotteryConfigBoardService lotteryConfigBoardService;

    @Operation(summary = "分页查询")
    @PostMapping("/queryPage")
    @SaCheckPermission("lotteryConfig:query")
    public ResponseDTO<PageResult<LotteryConfigVO>> queryPage(@RequestBody @Valid LotteryConfigQueryForm queryForm) {
        PageResult<LotteryConfigDTO> page = Service.queryPage(SolvelaBeanUtil.copy(queryForm, LotteryConfigQuery.class));
        return ResponseDTO.ok(SolvelaPageUtil.convert2PageResult(page, LotteryConfigVO.class));
    }

    @Operation(summary = "玩法一览：号码空间占用、期号与发号实况、参与人数与体检告警，列表页主视图")
    @PostMapping("/board")
    @SaCheckPermission("lotteryConfig:query")
    public ResponseDTO<LotteryConfigBoardResultDTO> board(@RequestBody @Valid LotteryConfigQueryForm queryForm) {
        return ResponseDTO.ok(lotteryConfigBoardService.board(SolvelaBeanUtil.copy(queryForm, LotteryConfigQuery.class)));
    }

    // ==================== 彩票工作台 ====================

    @Operation(summary = "生成彩票编码：10位大写字母+数字，服务端已判重")
    @GetMapping("/generateCode")
    @SaCheckPermission("lotteryConfig:query")
    public ResponseDTO<String> generateCode() {
        return Service.generateCode();
    }

    @Operation(summary = "玩法下拉：按活动过滤，工作台顶部二级切换用")
    @GetMapping("/optionList")
    @SaCheckPermission("lotteryConfig:query")
    public ResponseDTO<List<LotteryConfigOptionDTO>> optionList(@RequestParam String activityCode) {
        return Service.optionList(activityCode);
    }

    @Operation(summary = "工作台聚合回显：lotteryCode 为空表示在该活动下新建玩法，返回带预生成编码的空壳")
    @GetMapping("/workbench/detail")
    @SaCheckPermission("lotteryConfig:query")
    public ResponseDTO<LotteryWorkbenchDTO> workbenchDetail(@RequestParam String activityCode,
                                                           @RequestParam(required = false) String lotteryCode) {
        return Service.workbenchDetail(activityCode, lotteryCode);
    }

    @Operation(summary = "工作台聚合保存：彩票配置 + 奖级规则，单事务")
    @PostMapping("/workbench/save")
    @SaCheckPermission("lotteryConfig:update")
    public ResponseDTO<String> workbenchSave(@RequestBody @Valid LotteryWorkbenchSaveForm form) {
        // 🔴 deepCopy：本表单含嵌套集合（prizeRuleList），浅拷贝会因泛型不兼容
        // 跳过它，表现是"工作台保存成功，但奖励规则一条没建"
        return Service.workbenchSave(SolvelaBeanUtil.deepCopy(form, LotteryWorkbenchSaveCommand.class));
    }

    @Operation(summary = "上线：允许开始发号。上线前必须已配置奖级规则")
    @GetMapping("/online/{lotteryCode}")
    @SaCheckPermission("lotteryConfig:update")
    public ResponseDTO<String> online(@PathVariable String lotteryCode) {
        return Service.online(lotteryCode);
    }

    @Operation(summary = "下线：停止发号。已发出的号码不受影响，期号照常可以开奖")
    @GetMapping("/offline/{lotteryCode}")
    @SaCheckPermission("lotteryConfig:update")
    public ResponseDTO<String> offline(@PathVariable String lotteryCode) {
        return Service.offline(lotteryCode);
    }

    @Operation(summary = "批量下线：列表页的「批量禁用」，逐个下线并回一句汇总")
    @PostMapping("/batchOffline")
    @SaCheckPermission("lotteryConfig:update")
    public ResponseDTO<String> batchOffline(@RequestBody ValidateList<String> lotteryCodeList) {
        return Service.batchOffline(lotteryCodeList);
    }

    @Operation(summary = "FPE 算号推演：用固定演示期号算样例号码，与线上发号同一段代码")
    @PostMapping("/fpe/preview")
    @SaCheckPermission("lotteryConfig:query")
    public ResponseDTO<String> fpePreview(@RequestBody @Valid FpePreviewForm form) {
        return Service.fpePreview(SolvelaBeanUtil.copy(form, FpePreviewCommand.class));
    }

}
