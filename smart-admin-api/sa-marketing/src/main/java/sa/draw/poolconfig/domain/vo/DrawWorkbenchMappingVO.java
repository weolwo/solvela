package sa.draw.poolconfig.domain.vo;

import java.math.BigDecimal;

/**
 * 抽奖工作台回显 · Tab2 池内坑位
 * 存储层用 prize_item_id 关联，对外统一用 prizeCode，与保存契约(DrawWorkbenchMappingForm)对齐
 *
 * @param prizeCode   奖品编码
 * @param probability 中奖概率（百分比，如 10.95 表示 10.95%）
 * @param isFallback  是否兜底奖项：回显后前端恢复「自动配平」编辑态
 *
 * @Author alaric
 * @Date 2026-07-26
 */
public record DrawWorkbenchMappingVO(String prizeCode, BigDecimal probability, Boolean isFallback) {
}
