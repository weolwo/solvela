package net.lab1024.sa.lottery.config.domain.vo;

import java.math.BigDecimal;

/**
 * 彩票工作台 奖级规则回显节点
 *
 * 前四个字段与 LotteryWorkbenchRuleForm 同构，后三个是回查资产大库补齐的只读展示信息 ——
 * 与抽奖工作台一致：规则表只存 prize_code（SKU 化），名称/类型/价值都不冗余存储。
 *
 * @param prizeLevel  奖级，1 为最高
 * @param matchRule   EXACT / TAIL / HEAD
 * @param matchLength 匹配长度
 * @param prizeCode   奖品编码
 * @param prizeName   奖品名称（只读，来自 t_prize_config）
 * @param prizeType   奖品类型（只读）
 * @param prizeValue  奖品价值（只读）
 *
 * @Author alaric
 * @Date 2026-07-27
 */
public record LotteryWorkbenchRuleVO(Integer prizeLevel,
                                     String matchRule,
                                     Integer matchLength,
                                     String prizeCode,
                                     String prizeName,
                                     String prizeType,
                                     BigDecimal prizeValue) {
}
