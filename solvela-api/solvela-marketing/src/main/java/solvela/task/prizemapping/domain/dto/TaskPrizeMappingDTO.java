package solvela.task.prizemapping.domain.dto;

import java.math.BigDecimal;
import java.util.List;
import lombok.Data;

/**
 * 任务奖品映射列表的<b>读模型</b>：mapper 的 resultMap 映射到这里，service 也返回它。
 *
 * <p>DTO 是领域能查出来的全部，VO 是某个端决定给出去的那一部分，装配在端上做。
 * C 端将来接这条玩法时写自己的 VO，不必迁就管理端的字段。完整说明见 {@code MemberWalletDTO}。
 */

@Data
public class TaskPrizeMappingDTO {

    private Long id;

    /** 任务配置ID */
    private Long taskConfigId;

    /** 活动编码（来自 t_task_config） */
    private String activityCode;

    /** 任务名称（来自 t_task_config） */
    private String taskName;

    /** 任务状态：1-待生效, 2-生效中, 3-已下线（来自 t_task_config） */
    private Integer taskStatus;

    /** 任务阶段：单次任务填1，阶梯任务填 1, 2, 3... */
    private Integer stageLevel;

    /** 阶段达标条件原文 JSON */
    private String stageCondition;

    /** 达标值：从 stage_condition.target 拆出，拆不出为 null */
    private BigDecimal stageTarget;

    /** 奖励编码 */
    private String prizeCode;

    /** 奖品名称（来自 t_prize_config，按 活动+奖励编码 匹配；匹配不到为 null） */
    private String prizeName;

    /** 计算类型：FIXED(固定), RATIO(比例), FORMULA(公式) */
    private String prizeMode;

    /** 动态发奖策略原文 JSON */
    private String prizeStrategy;

    /** 奖励值：从 prize_strategy.value 拆出，拆不出为 null */
    private BigDecimal prizeValue;

    /** 配置体检结论，为空表示这一档没查出问题 */
    private List<String> issueList;

}
