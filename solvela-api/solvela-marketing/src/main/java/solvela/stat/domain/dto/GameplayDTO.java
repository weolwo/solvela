package solvela.stat.domain.dto;

import solvela.enums.IssueStatusEnum;
import solvela.enums.DrawResultEnum;
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

    /** 活动类型 BASIC/DRAW/TASK/LOTTERY */
    private String activityType;

    /** 抽奖状态分布（activityType=DRAW 时有值） */
    private List<DrawStatItem> drawStatList;

    /** 彩票期号售卖与开奖（activityType=LOTTERY 时有值） */
    private List<LotteryIssueItem> lotteryIssueList;

    /** 任务列表，供前端选一个看阶梯漏斗（activityType=TASK 时有值） */
    private List<TaskOption> taskOptionList;

    @Data
    /** 抽奖状态分布 */
    public static class DrawStatItem {

        /** 抽奖结果，对齐 t_draw_prize_log.status */
        private DrawResultEnum status;

        /** 记录条数 */
        private Integer count;

        /** 参与人数（按会员去重） */
        private Integer memberCount;
    }

    @Data
    /** 彩票期号 */
    public static class LotteryIssueItem {

        /** 彩票玩法编码。⚠️ 与活动编码是两套编码，一个活动可挂多个玩法 */
        private String lotteryCode;

        /** 彩票玩法名称 */
        private String lotteryName;

        /** 期号 */
        private String issueNo;

        /** 已售注数 */
        private Long soldCount;

        /** 该玩法发行总量 */
        private Long totalCount;

        /** 期号状态，对齐 t_lottery_issue.status。⚠️ 与上面 DrawStatItem.status 不是同一套字典 */
        private IssueStatusEnum status;

        /** 计划开奖时间 */
        private LocalDateTime planDrawTime;

        /** 逾期小时数，未逾期为 0 */
        private Long overdueHours;
    }

    @Data
    /** 任务选项 */
    public static class TaskOption {

        /** 任务配置ID */
        private Long taskConfigId;

        /** 任务名称 */
        private String taskName;

        /** 参与人数（有任务记录的人数） */
        private Integer memberCount;
    }
}
