package sa.lottery.prizerule.controller;

import sa.lottery.prizerule.domain.form.LotteryPrizeRuleQueryForm;
import sa.lottery.prizerule.domain.vo.LotteryPrizeAnalysisResultVO;
import sa.lottery.prizerule.domain.vo.LotteryPrizeRuleVO;
import sa.lottery.prizerule.service.LotteryPrizeAnalysisService;
import sa.lottery.prizerule.service.LotteryPrizeRuleService;
import sa.base.common.domain.ResponseDTO;
import sa.base.common.domain.PageResult;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import cn.dev33.satoken.annotation.SaCheckPermission;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * 彩票奖励配置 Controller —— <b>只读</b>。
 *
 * <h3>⚠️ add / update / delete / batchDelete 已刻意移除，不要再加回来</h3>
 * 奖级规则的唯一写入口是<b>彩票配置工作台</b>
 * （{@code LotteryConfigService.saveWorkbench}）。这里曾经并存的那套写接口有两个致命问题：
 * <ol>
 *   <li><b>它绕过了全部校验。</b>工作台的 {@code validateRules} 会拦下奖级 99、匹配长度超过号码长度、
 *       奖品不在本活动资产库等六类问题；而原先这里的 Service 只是 copy + insert，一条校验都没有。
 *       写进去的脏规则后果是实打实的：匹配规则非法 → 开奖时该奖级被整条跳过；
 *       奖品编码不存在 → 派奖时报「奖品配置不存在」，<b>用户中了奖拿不到东西</b>。</li>
 *   <li><b>它写进去的数据活不过下一次工作台保存。</b>工作台按玩法整表重建奖级
 *       （先 {@code delete where lottery_code = ?} 再重插），
 *       所以从这里加的规则会被静默删掉 —— 两个写入口在互相打架。</li>
 * </ol>
 * 保留一个「能写但会被覆盖、且不做校验」的入口，比没有这个入口危险得多。
 * 本页现在的职责是<b>跨玩法的赔付模型与配置体检</b>，只读即可胜任。
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

    private final LotteryPrizeAnalysisService lotteryPrizeAnalysisService;

    @Operation(summary = "分页查询：奖级规则原始行，保留给排查与导出用")
    @PostMapping("/queryPage")
    @SaCheckPermission("lotteryPrizeRule:query")
    public ResponseDTO<PageResult<LotteryPrizeRuleVO>> queryPage(@RequestBody @Valid LotteryPrizeRuleQueryForm queryForm) {
        return ResponseDTO.ok(Service.queryPage(queryForm));
    }

    @Operation(summary = "奖励结构分析：按玩法给出净中奖率、预计中奖注数、预计赔付成本与配置体检告警")
    @PostMapping("/analysis")
    @SaCheckPermission("lotteryPrizeRule:query")
    public ResponseDTO<LotteryPrizeAnalysisResultVO> analysis(@RequestBody @Valid LotteryPrizeRuleQueryForm queryForm) {
        return ResponseDTO.ok(lotteryPrizeAnalysisService.analysis(queryForm));
    }
}
