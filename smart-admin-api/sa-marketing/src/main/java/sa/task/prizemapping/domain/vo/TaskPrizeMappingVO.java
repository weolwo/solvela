package sa.task.prizemapping.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.util.List;
import lombok.Data;

/**
 * 任务阶段与奖励映射表 列表VO
 *
 * <p>本页是只读的巡检视图：除了子表自身的列，还带上主表（活动/任务名/任务状态）与
 * 奖品配置（奖品名）—— 单看子表那几个 ID 和一坨 JSON，运营看不出这条配置是给谁发什么。
 *
 * @Author weolwo
 * @Date 2026-04-18 20:41:02
 * @Copyright weolwo
 */

@Data
public class TaskPrizeMappingVO {

    @Schema(description = "id")
    private Long id;

    @Schema(description = "任务配置ID")
    private Long taskConfigId;

    @Schema(description = "活动编码（来自 t_task_config）")
    private String activityCode;

    @Schema(description = "任务名称（来自 t_task_config）")
    private String taskName;

    @Schema(description = "任务状态：1-待生效, 2-生效中, 3-已下线（来自 t_task_config）")
    private Integer taskStatus;

    @Schema(description = "任务阶段：单次任务填1，阶梯任务填 1, 2, 3...")
    private Integer stageLevel;

    @Schema(description = "阶段达标条件原文 JSON")
    private String stageCondition;

    @Schema(description = "达标值：从 stage_condition.target 拆出，拆不出为 null")
    private BigDecimal stageTarget;

    @Schema(description = "奖励编码")
    private String prizeCode;

    @Schema(description = "奖品名称（来自 t_prize_config，按 活动+奖励编码 匹配；匹配不到为 null）")
    private String prizeName;

    @Schema(description = "计算类型：FIXED(固定), RATIO(比例), FORMULA(公式)")
    private String prizeMode;

    @Schema(description = "动态发奖策略原文 JSON")
    private String prizeStrategy;

    @Schema(description = "奖励值：从 prize_strategy.value 拆出，拆不出为 null")
    private BigDecimal prizeValue;

    @Schema(description = "配置体检结论，为空表示这一档没查出问题")
    private List<String> issueList;

}
