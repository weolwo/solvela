package solvela.task.record.domain.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 任务记录列表的<b>读模型</b>：mapper 的 resultMap 映射到这里，service 也返回它。
 *
 * <p>DTO 是领域能查出来的全部，VO 是某个端决定给出去的那一部分，装配在端上做。
 * C 端将来接这条玩法时写自己的 VO，不必迁就管理端的字段。完整说明见 {@code MemberWalletDTO}。
 */

@Data
public class TaskRecordDTO {


    private Long id;

    /** 会员号 */
    private Long memberId;

    /**
     * 账号 —— join {@code t_member} 取的<b>当前值</b>（本表是状态表，不留快照）。
     * 会员被清理/注销时可能为空，前端要能接受空值。
     */
    private String memberName;

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

    /** 状态：0-进行中, 1-已完成, 2-已发奖, 3-已过期 */
    private Integer status;

    /** 进度详情 */
    private String progressData;

    /** 接取任务时的规则快照 */
    private String ruleSnapshot;

    /** 接取任务时的奖励快照 */
    private String prizeSnapshot;

    /** 达标时间 */
    private LocalDateTime completeTime;

    /** 创建人 */
    private String createBy;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新人 */
    private String updateBy;

    /** 更新时间 */
    private LocalDateTime updateTime;

}
