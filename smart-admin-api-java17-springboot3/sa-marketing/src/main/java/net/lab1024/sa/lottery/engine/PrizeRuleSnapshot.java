package net.lab1024.sa.lottery.engine;

import net.lab1024.sa.lottery.constant.LotteryConst;

/**
 * 奖级规则快照（引擎输入的不可变值对象），对应 t_lottery_prize_rule 的一行。
 *
 * 刻意不直接用实体：engine 包保持零 MyBatis / 零 Spring 依赖，才能纯内存单测。
 *
 * @param prizeLevel  奖级，1 为最高。禁止取 {@link LotteryConst#PRIZE_LEVEL_NONE}——那是「未中奖」的占位值
 * @param matchRule   匹配规则
 * @param matchLength 匹配长度，取值 [1, numberLength]；EXACT 时恒等于号码长度
 * @param prizeCode   命中后要发的奖品编码
 *
 * @Author alaric
 * @Date 2026-07-27
 */
public record PrizeRuleSnapshot(int prizeLevel, MatchRuleEnum matchRule, int matchLength, String prizeCode) {

    public PrizeRuleSnapshot {
        if (prizeLevel < 1) {
            throw new IllegalArgumentException("奖级必须从 1 开始，当前 " + prizeLevel);
        }
        if (prizeLevel == LotteryConst.PRIZE_LEVEL_NONE) {
            throw new IllegalArgumentException("奖级不能取 " + LotteryConst.PRIZE_LEVEL_NONE
                    + "：该值被「未中奖」占用，否则 C 端无法区分未中奖与 99 等奖");
        }
        if (matchRule == null) {
            throw new IllegalArgumentException("奖级 " + prizeLevel + " 的匹配规则非法");
        }
        if (matchLength < 1) {
            throw new IllegalArgumentException("奖级 " + prizeLevel + " 的匹配长度必须大于 0，当前 " + matchLength);
        }
        if (prizeCode == null || prizeCode.isBlank()) {
            throw new IllegalArgumentException("奖级 " + prizeLevel + " 未绑定奖品");
        }
    }
}
