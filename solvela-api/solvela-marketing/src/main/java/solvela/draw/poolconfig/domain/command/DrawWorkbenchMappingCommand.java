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

    /** 是否兜底奖项（编辑期概念，概率已配平为具体数值，暂不落库） */
    private Boolean isFallback;
}
