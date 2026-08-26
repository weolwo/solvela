package solvela.stat.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 玩法运行态：按活动类型返回对应的一块，其余为 null
 *
 * <p>刻意不做「全局跨玩法转化漏斗」：跨玩法的统一漏斗需要曝光埋点（营销中台不接，属上游），
 * 且任务侧幂等命中不写流水会让分母偏小 —— 算出来的转化率是错的（方案 §9「明确不做」）。
 *
 * @Author weolwo
 * @Date 2026-08-03
 */
@Data
public class GameplayDTO {

    @Schema(description = "活动类型 BASIC/DRAW/TASK/LOTTERY")
    private String activityType;

    @Schema(description = "抽奖状态分布（activityType=DRAW 时有值）")
    private List<DrawStatItem> drawStatList;

    @Schema(description = "彩票期号售卖与开奖（activityType=LOTTERY 时有值）")
    private List<LotteryIssueItem> lotteryIssueList;

    @Schema(description = "任务列表，供前端选一个看阶梯漏斗（activityType=TASK 时有值）")
    private List<TaskOption> taskOptionList;

    @Data
    @Schema(description = "抽奖状态分布")
    public static class DrawStatItem {

        @Schema(description = "状态 0-未中奖 1-已中奖 2-库存不足 3-异常")
        private Integer status;

        @Schema(description = "记录条数")
        private Integer count;

        @Schema(description = "参与人数（按会员去重）")
        private Integer memberCount;
    }

    @Data
    @Schema(description = "彩票期号")
    public static class LotteryIssueItem {

        @Schema(description = "彩票玩法编码。⚠️ 与活动编码是两套编码，一个活动可挂多个玩法")
        private String lotteryCode;

        @Schema(description = "彩票玩法名称")
        private String lotteryName;

        @Schema(description = "期号")
        private String issueNo;

        @Schema(description = "已售注数")
        private Long soldCount;

        @Schema(description = "该玩法发行总量")
        private Long totalCount;

        @Schema(description = "期号状态 0-销售中 1-开奖中 2-已开奖")
        private Integer status;

        @Schema(description = "计划开奖时间")
        private LocalDateTime planDrawTime;

        @Schema(description = "逾期小时数，未逾期为 0")
        private Long overdueHours;
    }

    @Data
    @Schema(description = "任务选项")
    public static class TaskOption {

        @Schema(description = "任务配置ID")
        private Long taskConfigId;

        @Schema(description = "任务名称")
        private String taskName;

        @Schema(description = "参与人数（有任务记录的人数）")
        private Integer memberCount;
    }
}
