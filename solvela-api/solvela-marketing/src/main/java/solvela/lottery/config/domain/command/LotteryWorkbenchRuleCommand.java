package solvela.lottery.config.domain.command;

import lombok.Data;

/**
 * 彩票工作台 奖级规则节点（t_lottery_prize_rule 的一行）
 *
 * @Author alaric
 * @Date 2026-07-27
 */
@Data
public class LotteryWorkbenchRuleCommand {

    /** 奖级，1 为最高 */
    private Integer prizeLevel;

    /** 匹配规则：EXACT-全号 / TAIL-尾号 / HEAD-首号 */
    private String matchRule;

    /** 匹配长度，EXACT 时等于号码长度 */
    private Integer matchLength;

    /** 绑定的奖品编码 */
    private String prizeCode;
}
