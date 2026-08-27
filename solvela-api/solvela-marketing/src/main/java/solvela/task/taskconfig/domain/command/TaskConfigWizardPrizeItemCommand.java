package solvela.task.taskconfig.domain.command;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 任务配置向导 奖励阶梯子表项（t_task_prize_mapping）
 *
 * @Author alaric
 * @Date 2026-07-19
 */
@Data
public class TaskConfigWizardPrizeItemCommand {

    /** 阶梯层级，从1开始 */
    private Integer stageLevel;

    /** 达标条件数值：COUNT型为次数，AMOUNT型为金额 */
    private Integer stageCondition;

    /** 奖励编码 */
    private String prizeCode;

    /** 计算类型：FIXED(固定), RATIO(比例), FORMULA(公式) */
    private String prizeMode;

    /** 奖励额度 */
    private BigDecimal prizeValue;
}
