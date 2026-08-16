package net.lab1024.sa.draw.poolitem.controller;

import net.lab1024.sa.draw.poolitem.domain.form.PrizePoolItemQueryForm;
import net.lab1024.sa.draw.poolitem.domain.vo.PrizeItemStockResultVO;
import net.lab1024.sa.draw.poolitem.domain.vo.PrizePoolItemVO;
import net.lab1024.sa.draw.poolitem.service.PrizeItemStockService;
import net.lab1024.sa.draw.poolitem.service.PrizePoolItemService;
import net.lab1024.sa.base.common.domain.ResponseDTO;
import net.lab1024.sa.base.common.domain.PageResult;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import cn.dev33.satoken.annotation.SaCheckPermission;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * 奖池奖项库 Controller —— <b>只读</b>。
 *
 * <h3>⚠️ add / update / delete / batchDelete 已刻意移除，不要再加回来</h3>
 * 奖项与库存的唯一写入口是<b>抽奖工作台</b>（{@code PrizePoolConfigService.workbenchSave}）。
 * 原先这里那套写接口是纯 {@code copy + dao}、零校验，问题比看上去严重：
 * <ol>
 *   <li><b>表单直接接受 {@code usedStock}。</b>它是跨奖池累计已出数量，
 *       是库存对账的基准。工作台的落库代码明写「used_stock/version 永不接受前端值」，
 *       而这条路径照单全收 —— 手改一个数，库存账目当场错乱，
 *       而真正决定能否抽到的 Redis 剩余量根本不会跟着变。</li>
 *   <li><b>delete 没有任何守卫。</b>删掉一个仍被坑位映射引用的奖项，
 *       抽奖会直接返回「奖池配置异常：奖项已被删除」。</li>
 *   <li><b>绕过上线结构锁。</b>工作台在活动已上线时限制「库存只允许追加，不允许缩减」，
 *       这条路径完全不看活动状态。</li>
 * </ol>
 * 本页现在的职责是<b>库存看板</b>：两个口径的剩余量对比、消耗率、售罄预警、
 * 跨奖池引用关系 —— 只读即可胜任，而且这些恰恰是原页面看不到的东西。
 *
 * @Author weolwo
 * @Date 2026-04-19 09:52:45
 * @Copyright weolwo
 */
@RequiredArgsConstructor
@RestController
@Tag(name = "奖池奖项库")
@RequestMapping("/prizePoolItem")
public class PrizePoolItemController {

    private final PrizePoolItemService Service;

    private final PrizeItemStockService prizeItemStockService;

    @Operation(summary = "分页查询：奖项原始行，保留给排查与导出用")
    @PostMapping("/queryPage")
    @SaCheckPermission("prizePoolItem:query")
    public ResponseDTO<PageResult<PrizePoolItemVO>> queryPage(@RequestBody @Valid PrizePoolItemQueryForm queryForm) {
        return ResponseDTO.ok(Service.queryPage(queryForm));
    }

    @Operation(summary = "库存看板：Redis/DB 双口径剩余、消耗率、售罄预警、跨奖池引用与体检告警")
    @PostMapping("/stockBoard")
    @SaCheckPermission("prizePoolItem:query")
    public ResponseDTO<PrizeItemStockResultVO> stockBoard(@RequestBody @Valid PrizePoolItemQueryForm queryForm) {
        return ResponseDTO.ok(prizeItemStockService.stockBoard(queryForm));
    }
}
