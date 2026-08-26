package solvela.admin.module.draw.prizemapping.controller;

import solvela.draw.prizemapping.domain.form.PoolPrizeMappingQueryForm;
import solvela.draw.prizemapping.domain.vo.DrawPoolAnalysisResultVO;
import solvela.draw.prizemapping.domain.vo.PoolPrizeMappingVO;
import solvela.draw.prizemapping.service.DrawPoolAnalysisService;
import solvela.draw.prizemapping.service.PoolPrizeMappingService;
import solvela.base.domain.ResponseDTO;
import solvela.base.domain.PageResult;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import cn.dev33.satoken.annotation.SaCheckPermission;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * 奖池奖项映射 Controller —— <b>只读</b>。
 *
 * <h3>⚠️ add / update / delete / batchDelete 已刻意移除，不要再加回来</h3>
 * 坑位映射的唯一写入口是<b>抽奖工作台</b>（{@code PrizePoolConfigService.workbenchSave}）。
 * 原先这里那套写接口是纯 {@code copy + dao}、零校验，而它能造成的后果是全模块最严重的：
 * <ol>
 *   <li><b>能把线上活动直接打挂。</b>把任意一条概率从 30 改成 31，该池概率总和就不再是 100%。
 *       {@code DrawPoolSnapshot} 的构造函数发现未闭环会抛 {@code IllegalArgumentException}，
 *       而 {@code DrawExecuteService} 的执行路径<b>没有捕获它</b> ——
 *       这个奖池的每一次抽奖请求都会直接报错。一个下拉框就是一个线上开关。</li>
 *   <li><b>能绕过上线结构锁。</b>工作台在活动已上线时禁止增删坑位（概率可调），
 *       这条路径完全不看活动状态。</li>
 *   <li><b>能配出多个兜底。</b>引擎 {@code fallbackPrize()} 只取坑位顺序里的第一个，
 *       其余静默失效 —— 配了以为有兜底，实际没有。</li>
 *   <li><b>写进去也活不长。</b>工作台按池整表重建坑位（先 delete 后插），
 *       从这里加的映射会被静默删掉。</li>
 * </ol>
 * 保留一个「能写、不校验、且会被覆盖」的入口，比没有这个入口危险得多。
 * 本页现在的职责是<b>跨奖池的概率结构分析与体检</b>，只读即可胜任。
 *
 * @Author weolwo
 * @Date 2026-04-19 10:07:03
 * @Copyright weolwo
 */
@RequiredArgsConstructor
@RestController
@Tag(name = "奖池奖项映射")
@RequestMapping("/poolPrizeMapping")
public class PoolPrizeMappingController {

    private final PoolPrizeMappingService Service;

    private final DrawPoolAnalysisService drawPoolAnalysisService;

    @Operation(summary = "分页查询：坑位映射原始行，保留给排查与导出用")
    @PostMapping("/queryPage")
    @SaCheckPermission("poolPrizeMapping:query")
    public ResponseDTO<PageResult<PoolPrizeMappingVO>> queryPage(@RequestBody @Valid PoolPrizeMappingQueryForm queryForm) {
        return ResponseDTO.ok(Service.queryPage(queryForm));
    }

    @Operation(summary = "奖池概率分析：按池给出命中区间、期望赔付、库存口径对比与配置体检告警")
    @PostMapping("/analysis")
    @SaCheckPermission("poolPrizeMapping:query")
    public ResponseDTO<DrawPoolAnalysisResultVO> analysis(@RequestBody @Valid PoolPrizeMappingQueryForm queryForm) {
        return ResponseDTO.ok(drawPoolAnalysisService.analysis(queryForm));
    }
}
