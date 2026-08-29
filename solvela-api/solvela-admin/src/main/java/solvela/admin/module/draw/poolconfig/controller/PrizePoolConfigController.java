package solvela.admin.module.draw.poolconfig.controller;

import solvela.base.domain.ValidateList;
import solvela.draw.PrizePoolConfig;
import solvela.draw.poolconfig.domain.dto.PrizePoolBoardResultDTO;
import solvela.draw.poolconfig.service.PrizePoolBoardService;
import solvela.admin.module.draw.poolconfig.domain.form.DrawWorkbenchSaveForm;
import solvela.draw.poolconfig.domain.command.DrawWorkbenchSaveCommand;
import solvela.admin.module.draw.poolconfig.domain.form.PrizePoolConfigAddForm;
import solvela.draw.poolconfig.domain.command.PrizePoolConfigAddCommand;
import solvela.admin.module.draw.poolconfig.domain.form.PrizePoolConfigQueryForm;
import solvela.draw.poolconfig.domain.query.PrizePoolConfigQuery;
import solvela.admin.module.draw.poolconfig.domain.form.PrizePoolConfigUpdateForm;
import solvela.draw.poolconfig.domain.command.PrizePoolConfigUpdateCommand;
import solvela.draw.poolconfig.domain.dto.DrawWorkbenchDTO;
import solvela.admin.module.draw.poolconfig.domain.vo.PrizePoolConfigVO;
import solvela.draw.poolconfig.domain.dto.PrizePoolConfigDTO;
import solvela.draw.poolconfig.service.PrizePoolConfigService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import solvela.base.util.SolvelaBeanUtil;
import solvela.base.dao.SolvelaPageUtil;
import solvela.base.domain.PageResult;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import solvela.web.RequiresPermission;
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
    @RequiresPermission("prizePoolConfig:query")
    public PageResult<PrizePoolConfigVO> queryPage(@RequestBody @Valid PrizePoolConfigQueryForm queryForm) {
        PageResult<PrizePoolConfigDTO> page = Service.queryPage(SolvelaBeanUtil.copy(queryForm, PrizePoolConfigQuery.class));
        return SolvelaPageUtil.convert2PageResult(page, PrizePoolConfigVO.class);
    }

    @Operation(summary = "奖池一览：可编辑字段 + 坑位/概率/限领搭配的体检结论，列表页主视图")
    @PostMapping("/board")
    @RequiresPermission("prizePoolConfig:query")
    public PrizePoolBoardResultDTO board(@RequestBody @Valid PrizePoolConfigQueryForm queryForm) {
        return prizePoolBoardService.board(SolvelaBeanUtil.copy(queryForm, PrizePoolConfigQuery.class));
    }

    @Operation(summary = "生成奖池编码（10位大写字母+数字，已判重）")
    @GetMapping("/generateCode")
    @RequiresPermission("prizePoolConfig:workbench:save")
    public String generatePoolCode() {
        return Service.generatePoolCode();
    }

    @Operation(summary = "抽奖工作台聚合回显（与聚合保存入参同构）")
    @GetMapping("/workbench/detail")
    @RequiresPermission("prizePoolConfig:query")
    public DrawWorkbenchDTO workbenchDetail(@RequestParam String activityCode) {
        return Service.workbenchDetail(activityCode);
    }

    @Operation(summary = "抽奖工作台聚合保存（物资 + 多奖池 + 坑位映射，主子表事务）")
    @PostMapping("/workbench/save")
    @RequiresPermission("prizePoolConfig:workbench:save")
    public void workbenchSave(@RequestBody @Valid DrawWorkbenchSaveForm saveForm) {
        // 🔴 用 deepCopy 而不是 copy：本表单有两层嵌套（poolList -> mappingList），
        // 浅拷贝会因泛型不兼容<b>跳过</b>这些集合，结果是"保存成功但奖池一个没建"
        Service.workbenchSave(SolvelaBeanUtil.deepCopy(saveForm, DrawWorkbenchSaveCommand.class));
    }

    @Operation(summary = "更新")
    @PostMapping("/update")
    @RequiresPermission("prizePoolConfig:update")
    public void update(@RequestBody @Valid PrizePoolConfigUpdateForm updateForm) {
        Service.update(SolvelaBeanUtil.copy(updateForm, PrizePoolConfigUpdateCommand.class));
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
    @RequiresPermission("prizePoolConfig:update")
    public void offline(@PathVariable Long id) {
        Service.offline(id);
    }

    @Operation(summary = "启用奖池：把开关拨回开启")
    @GetMapping("/online/{id}")
    @RequiresPermission("prizePoolConfig:update")
    public void online(@PathVariable Long id) {
        Service.online(id);
    }

    @Operation(summary = "批量禁用：逐个禁用并回一句汇总，本就已关闭的计入跳过")
    @PostMapping("/batchOffline")
    @RequiresPermission("prizePoolConfig:update")
    public String batchOffline(@RequestBody ValidateList<Long> idList) {
        return Service.batchOffline(idList);
    }
}
