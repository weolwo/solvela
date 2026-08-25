package solvela.stat.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 任务阶梯流失漏斗
 *
 * <p>直接回答「1000 人签到 1 天，只有 50 人连到 7 天」，运营据此调难度。
 *
 * <p>⚠️ 「到达人数」的判据是 {@code current_metric >= 该档的 target}，
 * 不是「发过这档的奖」—— 发奖可能因为预算、审批停在半路，那是发奖健康度要回答的问题，
 * 混进漏斗会让运营把「发不出去」误读成「用户没做到」。
 *
 * @Author weolwo
 * @Date 2026-08-03
 */
@Data
public class TaskFunnelVO {

    @Schema(description = "任务配置ID")
    private Long taskConfigId;

    @Schema(description = "任务名称")
    private String taskName;

    @Schema(description = "接取人数：有任务记录的人数（漏斗第一层）")
    private Integer joinedCount;

    @Schema(description = "各阶梯档位，按 stage_level 升序")
    private List<StageItem> stageList;

    @Data
    @Schema(description = "一个阶梯档位")
    public static class StageItem {

        @Schema(description = "档位序号")
        private Integer stageLevel;

        @Schema(description = "达标条件，取自 stage_condition.target")
        private BigDecimal target;

        @Schema(description = "该档发什么奖")
        private String prizeCode;

        @Schema(description = "到达人数：current_metric >= target 的人数")
        private Integer reachCount;
    }
}
