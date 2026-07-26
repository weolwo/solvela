package net.lab1024.sa.lottery.prizerule.controller;

import net.lab1024.sa.base.common.domain.ValidateList;
import net.lab1024.sa.lottery.prizerule.domain.entity.LotteryPrizeRule;
import net.lab1024.sa.lottery.prizerule.domain.form.LotteryPrizeRuleAddForm;
import net.lab1024.sa.lottery.prizerule.domain.form.LotteryPrizeRuleQueryForm;
import net.lab1024.sa.lottery.prizerule.domain.form.LotteryPrizeRuleUpdateForm;
import net.lab1024.sa.lottery.prizerule.domain.vo.LotteryPrizeRuleVO;
import net.lab1024.sa.lottery.prizerule.service.LotteryPrizeRuleService;
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
 * 彩票奖励配置 Controller
 *
 * @Author weolwo
 * @Date 2026-04-19 11:50:34
 * @Copyright weolwo
 */
@RequiredArgsConstructor
@RestController
@Tag(name = "彩票奖励配置")
@RequestMapping("/lotteryPrizeRule")
public class LotteryPrizeRuleController {

    private final LotteryPrizeRuleService Service;

    @Operation(summary = "分页查询")
    @PostMapping("/queryPage")
    @SaCheckPermission(":query")
    public ResponseDTO<PageResult<LotteryPrizeRuleVO>> queryPage(@RequestBody @Valid LotteryPrizeRuleQueryForm queryForm) {
        return ResponseDTO.ok(Service.queryPage(queryForm));
    }

    @Operation(summary = "添加")
    @PostMapping("/add")
    @SaCheckPermission(":add")
    public ResponseDTO<String> add(@RequestBody @Valid LotteryPrizeRuleAddForm addForm) {
        return Service.add(addForm);
    }

    @Operation(summary = "更新")
    @PostMapping("/update")
    @SaCheckPermission(":update")
    public ResponseDTO<String> update(@RequestBody @Valid LotteryPrizeRuleUpdateForm updateForm) {
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
