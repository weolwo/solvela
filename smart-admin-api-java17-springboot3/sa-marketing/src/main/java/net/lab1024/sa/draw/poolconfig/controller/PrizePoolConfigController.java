package net.lab1024.sa.draw.poolconfig.controller;

import net.lab1024.sa.base.common.domain.ValidateList;
import net.lab1024.sa.draw.poolconfig.domain.entity.PrizePoolConfig;
import net.lab1024.sa.draw.poolconfig.domain.vo.PrizePoolBoardResultVO;
import net.lab1024.sa.draw.poolconfig.service.PrizePoolBoardService;
import net.lab1024.sa.draw.poolconfig.domain.form.DrawWorkbenchSaveForm;
import net.lab1024.sa.draw.poolconfig.domain.form.PrizePoolConfigAddForm;
import net.lab1024.sa.draw.poolconfig.domain.form.PrizePoolConfigQueryForm;
import net.lab1024.sa.draw.poolconfig.domain.form.PrizePoolConfigUpdateForm;
import net.lab1024.sa.draw.poolconfig.domain.vo.DrawWorkbenchVO;
import net.lab1024.sa.draw.poolconfig.domain.vo.PrizePoolConfigVO;
import net.lab1024.sa.draw.poolconfig.service.PrizePoolConfigService;
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
 * 奖池配置 Controller
 *
 * @Author weolwo
 * @Date 2026-04-19 09:42:12
 * @Copyright weolwo
 */
@RequiredArgsConstructor
@RestController
@Tag(name = "奖池配置")
@RequestMapping("/prizePoolConfig")
public class PrizePoolConfigController {

    private final PrizePoolConfigService Service;

    private final PrizePoolBoardService prizePoolBoardService;

    @Operation(summary = "分页查询：奖池原始行，供下拉选项等场景使用")
    @PostMapping("/queryPage")
    @SaCheckPermission("prizePoolConfig:query")
    public ResponseDTO<PageResult<PrizePoolConfigVO>> queryPage(@RequestBody @Valid PrizePoolConfigQueryForm queryForm) {
        return ResponseDTO.ok(Service.queryPage(queryForm));
    }

    @Operation(summary = "奖池一览：可编辑字段 + 坑位/概率/限领搭配的体检结论，列表页主视图")
    @PostMapping("/board")
    @SaCheckPermission("prizePoolConfig:query")
    public ResponseDTO<PrizePoolBoardResultVO> board(@RequestBody @Valid PrizePoolConfigQueryForm queryForm) {
        return ResponseDTO.ok(prizePoolBoardService.board(queryForm));
    }

    @Operation(summary = "生成奖池编码（10位大写字母+数字，已判重）")
    @GetMapping("/generateCode")
    @SaCheckPermission("prizePoolConfig:workbench:save")
    public ResponseDTO<String> generatePoolCode() {
        return Service.generatePoolCode();
    }

    @Operation(summary = "抽奖工作台聚合回显（与聚合保存入参同构）")
    @GetMapping("/workbench/detail")
    @SaCheckPermission("prizePoolConfig:query")
    public ResponseDTO<DrawWorkbenchVO> workbenchDetail(@RequestParam String activityCode) {
        return Service.workbenchDetail(activityCode);
    }

    @Operation(summary = "抽奖工作台聚合保存（物资 + 多奖池 + 坑位映射，主子表事务）")
    @PostMapping("/workbench/save")
    @SaCheckPermission("prizePoolConfig:workbench:save")
    public ResponseDTO<String> workbenchSave(@RequestBody @Valid DrawWorkbenchSaveForm saveForm) {
        return Service.workbenchSave(saveForm);
    }

    @Operation(summary = "更新")
    @PostMapping("/update")
    @SaCheckPermission("prizePoolConfig:update")
    public ResponseDTO<String> update(@RequestBody @Valid PrizePoolConfigUpdateForm updateForm) {
        return Service.update(updateForm);
    }

    /*
     * ⚠️ add / delete / batchDelete 已移除，取而代之的是下面三个：
     *
     *  - 新建奖池 → 抽奖工作台。只有 t_prize_pool_config 一行、没有坑位映射的池，
     *    抽奖时快照构造直接抛「奖池快照不能为空」，是个建了就用不了的空壳；
     *    工作台把「池 + 坑位 + 概率闭环校验」放在一个事务里，那才是奖池的完整形态。
     *
     *  - 删除 → 禁用。删池会留下孤儿坑位映射，更要命的是 t_draw_prize_log 里存着 pool_code，
     *    那是发奖凭证：用户说「我明明在这个池抽中过」而那个池已不存在，客诉自证当场断掉。
     *    禁用则一个字都不动历史数据，运行态直接拒绝新请求，而且可逆。
     *    与彩票玩法「删除换成下线」是同一个决定（见 v3.63.0.sql）。
     *
     * 三个接口都用 prizePoolConfig:update，不新增权限点 ——
     * 「改配置」与「停用」对角色授权而言是同一件事（沿用 v3.63.0 的判断）。
     */

    @Operation(summary = "禁用奖池：关闭开关，运行态立即拒绝新的抽奖请求；历史流水与坑位不受影响，可逆")
    @GetMapping("/offline/{id}")
    @SaCheckPermission("prizePoolConfig:update")
    public ResponseDTO<String> offline(@PathVariable Long id) {
        return Service.offline(id);
    }

    @Operation(summary = "启用奖池：把开关拨回开启")
    @GetMapping("/online/{id}")
    @SaCheckPermission("prizePoolConfig:update")
    public ResponseDTO<String> online(@PathVariable Long id) {
        return Service.online(id);
    }

    @Operation(summary = "批量禁用：逐个禁用并回一句汇总，本就已关闭的计入跳过")
    @PostMapping("/batchOffline")
    @SaCheckPermission("prizePoolConfig:update")
    public ResponseDTO<String> batchOffline(@RequestBody ValidateList<Long> idList) {
        return Service.batchOffline(idList);
    }
}
