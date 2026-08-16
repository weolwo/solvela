package sa.task.taskconfig.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 任务配置向导 回显详情（主子表一次性返回）
 *
 * <p>结构刻意与提交表单 {@code TaskConfigWizardSubmitForm} 对称：向导拿到它就能直接铺回 5 个步骤，
 * 不用前端再去拼第二个接口的结果。
 *
 * <p>⚠️ 两处「反向拆解」由本 VO 负责，前端不该再猜：
 * <ul>
 *   <li>{@code stage_condition} 落库时是 {@code {"target": 3}}，这里还原成 {@code stageCondition = 3}</li>
 *   <li>{@code prize_strategy} 落库时是 {@code {"value": 100}}，这里还原成 {@code prizeValue = 100}</li>
 * </ul>
 * 而 taskDesc / ruleDesc 提交时被并进了 ui_config，这里也单独摘出来，
 * 免得前端在 uiConfig 里既要读展示字段又要读 schema 参数。
 *
 * @Author alaric
 * @Date 2026-08-15
 */
@Data
public class TaskConfigWizardDetailVO {

    @Schema(description = "任务配置ID")
    private Long id;

    @Schema(description = "活动编码（编辑态锁定，不可改）")
    private String activityCode;

    @Schema(description = "模板Code（编辑态锁定，不可改）")
    private String templateCode;

    @Schema(description = "任务名称")
    private String taskName;

    @Schema(description = "触发事件")
    private String triggerEvent;

    @Schema(description = "任务分组")
    private String taskGroup;

    @Schema(description = "目标人群")
    private String targetAudience;

    @Schema(description = "参与频次")
    private String limitType;

    @Schema(description = "限制次数")
    private Integer limitCount;

    @Schema(description = "排序权重")
    private Integer sortWeight;

    @Schema(description = "跳转地址")
    private String actionUrl;

    @Schema(description = "开始时间")
    private LocalDateTime startTime;

    @Schema(description = "结束时间")
    private LocalDateTime endTime;

    @Schema(description = "任务状态（只读回显，向导不改它）")
    private Integer status;

    @Schema(description = "C端任务说明（从 ui_config 摘出）")
    private String taskDesc;

    @Schema(description = "C端规则说明（从 ui_config 摘出）")
    private String ruleDesc;

    @Schema(description = "角标（从 ui_config 摘出）")
    private String badge;

    @Schema(description = "规则参数：ui_schema 里非图片类参数的取值")
    private Map<String, Object> ruleConfig;

    @Schema(description = "展示参数：ui_schema 里 image_upload 类参数的取值（已剔除 badge/taskDesc/ruleDesc）")
    private Map<String, Object> uiConfig;

    @Schema(description = "奖励阶梯")
    private List<PrizeLadder> prizeMappingList;

    @Data
    public static class PrizeLadder {

        @Schema(description = "阶梯层级，从1开始")
        private Integer stageLevel;

        @Schema(description = "达标条件数值")
        private Integer stageCondition;

        @Schema(description = "奖励编码")
        private String prizeCode;

        @Schema(description = "计算类型")
        private String prizeMode;

        @Schema(description = "奖励额度")
        private BigDecimal prizeValue;
    }
}
