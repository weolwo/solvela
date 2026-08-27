package solvela.lottery.config.domain.command;

import lombok.Data;
import solvela.base.util.SolvelaCodeUtil;

import java.util.List;

/**
 * 彩票工作台 聚合保存表单（主子表：t_lottery_config + t_lottery_prize_rule）
 *
 * 契约：{ lotteryCode, lotteryName, numberLength, totalCount, activityCode, prizeRuleList }
 *
 * ⚠️ 刻意不含 numberCharset：号码固定十进制，不是可配项（见 LotteryConst.NUMBER_CHARSET）。
 * 前端既不提交也不展示，服务端保存时固定写死，避免「界面摆着可选项、选了却报错」。
 *
 * @Author alaric
 * @Date 2026-07-27
 */
@Data
public class LotteryWorkbenchSaveCommand {

    /** 活动编码 */
    private String activityCode;

    /**
     * 彩票编码允许手工输入，故这里挂 @Pattern 做格式校验，Service 里还要再判一次重
     * （表上的唯一索引直接抛 SQL 异常对运营不友好）——这是铁律 8 的「落点清单」第 ① 项
     */
    private String lotteryCode;

    /** 彩票名称（外显） */
    private String lotteryName;

    /**
     * 上下限与 LotteryConst.MIN_NUMBER_LENGTH / MAX_NUMBER_LENGTH 同源。
     * 注解参数必须是编译期常量，故这里只能写字面量，改动时两处要一起改。
     */
    private Integer numberLength;

    /** 单期发售上限 */
    private Integer totalCount;

    /**
     * 允许为空：运营可以先把发号引擎配好，奖级规则稍后再配。
     * 但一旦提交了规则，就要整体重建（子表整表替换语义）
     */
    private List<LotteryWorkbenchRuleCommand> prizeRuleList;
}
