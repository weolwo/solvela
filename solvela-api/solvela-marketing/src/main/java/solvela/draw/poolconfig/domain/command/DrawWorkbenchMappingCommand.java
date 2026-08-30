package solvela.draw.poolconfig.domain.command;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 抽奖工作台 Tab2 奖池坑位映射项（t_pool_prize_mapping）
 * 前端以 prizeCode 表达关联，服务端保存时解析为 prize_item_id
 *
 * @Author alaric
 * @Date 2026-07-26
 */
@Data
public class DrawWorkbenchMappingCommand {

    /** 奖品编码，须存在于本次提交的物资列表中 */
    private String prizeCode;

    /** 中奖概率（百分比，0~100，支持4位小数） */
    private BigDecimal probability;

    /**
     * 是否兜底奖项。<b>落库</b>到 {@code t_pool_prize_mapping.is_fallback}，
     * 并且是运行期库存降级链路的<b>唯一依据</b>。
     *
     * <p>⚠️ 这里原先写着「编辑期概念，概率已配平为具体数值，暂不落库」——
     * 那句是过期的，照它理解会得出「兜底只是个 UI 概念」的错误结论。
     * 实际上 {@code PrizePoolConfigService.workbenchSave} 明确 setIsFallback 存了库，
     * 而 {@link solvela.draw.engine.DrawEngine} 与 {@code DrawExecuteService.settle}
     * 两处都要靠这一列找到降级目标。
     *
     * <h3>它在两个阶段各做一件事</h3>
     * <ol>
     *   <li><b>配置期：自动吃掉本池剩余概率。</b>这一步只在前端做
     *       （{@code PoolProbabilityEngine.vue} 的 balancePool，用 decimal.js 算
     *       {@code 100 - 其余奖项之和} 写回本项），所以兜底那一格的概率输入框是禁用的。
     *       服务端<b>不配平、只校验</b>：概率总和不等于 100% 或兜底超过一个一律打回；</li>
     *   <li><b>运行期：库存降级的接盘者。</b>概率命中的奖项快照无库存时降级到它
     *       （DrawEngine），Lua 预扣真的失败时再降级一次（DrawExecuteService.settle）。
     *       两处都有是必要的：引擎读快照做快速失败，真正的防超发在 Lua + DB 乐观锁那层，
     *       「快照说有货、实际扣不到」在高并发下是常态。</li>
     * </ol>
     *
     * <h3>🔴 兜底奖项的库存与限领都应该配成不限（-1）</h3>
     * 降级要求 {@code fallback.hasStock()} 且能真的扣减成功，所以<b>兜底不是无限兜底</b>：
     * 它自己的库存用完、或者这个人在它上面的单人限领用完，整条降级链就断了，
     * 用户拿到的是「手慢了，奖品已被抽完」。而限领耗尽那一档，这句提示还是错的 ——
     * 真实原因与库存无关，用户却会以为再快点就有。
     *
     * <p>这也是「谢谢参与」这类占位奖该用 {@code PrizeTypeEnum.MARKER} 的原因：
     * 它不动账，库存开无限没有任何成本。
     */
    private Boolean isFallback;
}
