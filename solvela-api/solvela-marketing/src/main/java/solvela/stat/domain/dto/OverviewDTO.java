package solvela.stat.domain.dto;

import solvela.enums.ActivityStatusEnum;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 大屏总览：活动状态分布 + 活动卡片 + 逾期未开奖告警
 *
 * <p>🔴 活动状态只有<b>启用 / 禁用</b>两态，判据是 {@code status = 2 → 禁用，其余（含历史值 0）→ 启用}。
 * 「有没有开始」由业务层按起止时间实时算，不是后台开关，故大屏不做时间窗判定
 * （与首页参与统计图、与活动列表页同口径）。
 *
 * @Author weolwo
 * @Date 2026-08-03
 */
@Data
public class OverviewDTO {

    /** 启用中的活动数（status != 2） */
    private Integer enabledCount;

    /** 已禁用的活动数（status = 2） */
    private Integer disabledCount;

    /** 活动卡片列表 */
    private List<ActivityCard> activityList;

    /** 逾期未开奖告警：已对外承诺开奖时刻却未开奖，属客诉级异常 */
    private List<OverdueIssue> overdueIssueList;

    @Data
    /** 活动卡片 */
    public static class ActivityCard {

        /** 活动编码 */
        private String activityCode;

        /** 活动名称 */
        private String activityName;

        /** 活动类型 BASIC/DRAW/TASK/LOTTERY */
        private String activityType;

        /** 原始状态值，1-启用 2-禁用 0-历史值（按启用处理） */
        private ActivityStatusEnum status;

        /** 是否启用，= status != 2。前端直接用它，不要自己再判一遍 */
        private Boolean enabled;

        /** 开始时间：只做展示，不参与状态判定 */
        private LocalDateTime startTime;

        /** 结束时间：只做展示，不参与状态判定 */
        private LocalDateTime endTime;

        /** 挂了几个玩法主体（奖池/任务/彩票玩法），来自 ActivityRefProvider */
        private Long gameplayCount;

        /** 玩法是否配置完备，判据是「能不能上线」而不是「有没有记录」 */
        private Boolean configured;
    }

    @Data
    /** 逾期未开奖的期号 */
    public static class OverdueIssue {

        /** 彩票玩法编码。⚠️ 与活动编码是两套编码，靠 t_lottery_config.activity_code 关联 */
        private String lotteryCode;

        /** 所属活动编码 */
        private String activityCode;

        /** 彩票玩法名称 */
        private String lotteryName;

        /** 期号 */
        private String issueNo;

        /** 计划开奖时间 */
        private LocalDateTime planDrawTime;

        /** 已逾期小时数 */
        private Long overdueHours;
    }
}
