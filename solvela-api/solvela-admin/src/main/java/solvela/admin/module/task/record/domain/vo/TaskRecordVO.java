package solvela.admin.module.task.record.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 任务记录表 列表VO
 *
 * @Author weolwo
 * @Date 2026-04-18 21:02:56
 * @Copyright weolwo
 */

@Data
public class TaskRecordVO {


    @Schema(description = "id")
    private Long id;

    @Schema(description = "会员号")
    private Long memberId;

    /**
     * 账号 —— join {@code t_member} 取的<b>当前值</b>（本表是状态表，不留快照）。
     * 会员被清理/注销时可能为空，前端要能接受空值。
     */
    @Schema(description = "会员账号（取自会员主表的当前值）")
    private String memberName;

    @Schema(description = "任务配置ID")
    private Long taskConfigId;

    @Schema(description = "活动编码")
    private String activityCode;

    @Schema(description = "业务期数标识(防重用)：NONE, 日期(20260402)")
    private String periodKey;

    @Schema(description = "开始时间")
    private LocalDateTime validStartTime;

    @Schema(description = "过期时间")
    private LocalDateTime validEndTime;

    @Schema(description = "当前进度值：如已签到 3.0000 天")
    private BigDecimal currentMetric;

    @Schema(description = "状态：0-进行中, 1-已完成, 2-已发奖, 3-已过期")
    private Integer status;

    @Schema(description = "进度详情")
    private String progressData;

    @Schema(description = "接取任务时的规则快照")
    private String ruleSnapshot;

    @Schema(description = "接取任务时的奖励快照")
    private String prizeSnapshot;

    @Schema(description = "达标时间")
    private LocalDateTime completeTime;

    @Schema(description = "创建人")
    private String createBy;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    @Schema(description = "更新人")
    private String updateBy;

    @Schema(description = "更新时间")
    private LocalDateTime updateTime;

}
