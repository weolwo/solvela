package solvela.task.record.domain.command;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 任务记录表 新建表单
 *
 * @Author weolwo
 * @Date 2026-04-18 21:02:56
 * @Copyright weolwo
 */

@Data
public class TaskRecordAddCommand {

    private Long id;

    /**
     * 会员号 —— 关联键。调用方只需给它，账号快照由服务端查会员表补
     * （见 {@code MemberService.requireMemberName}），这样快照与会员号<b>不可能对不上</b>。
     */
    private Long memberId;

    /** 任务配置ID */
    private Long taskConfigId;

    /** 活动编码 */
    private String activityCode;

    /** 业务期数标识(防重用)：NONE, 日期(20260402) */
    private String periodKey;

    /** 开始时间 */
    private LocalDateTime validStartTime;

    /** 过期时间 */
    private LocalDateTime validEndTime;

    /** 当前进度值：如已签到 3.0000 天 */
    private BigDecimal currentMetric;

    /** 进度详情 */
    private String progressData;

    /** 接取任务时的规则快照 */
    private String ruleSnapshot;

    /** 接取任务时的奖励快照 */
    private String prizeSnapshot;

}