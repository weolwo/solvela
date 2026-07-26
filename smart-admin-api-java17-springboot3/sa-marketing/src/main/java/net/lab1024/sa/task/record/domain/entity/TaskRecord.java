package net.lab1024.sa.task.record.domain.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 任务记录表 实体类
 *
 * @Author weolwo
 * @Date 2026-04-18 21:02:56
 * @Copyright weolwo
 */

@Data
@TableName("t_task_record")
public class TaskRecord {

    /**
     * id
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 租户ID
     */
    private String tenantId;

    /**
     * 会员名
     */
    private String memberName;

    /**
     * 任务配置ID
     */
    private Long taskConfigId;

    /**
     * 活动编码
     */
    private String activityCode;

    /**
     * 业务期数标识(防重用)：NONE, 日期(20260402)
     */
    private String periodKey;

    /**
     * 开始时间
     */
    private LocalDateTime validStartTime;

    /**
     * 过期时间
     */
    private LocalDateTime validEndTime;

    /**
     * 当前进度值：如已签到 3.0000 天
     */
    private BigDecimal currentMetric;

    /**
     * 状态：0-进行中, 1-已完成, 2-已发奖, 3-已过期
     */
    private Integer status;

    /**
     * 进度详情
     */
    private String progressData;

    /**
     * 接取任务时的规则快照
     */
    private String ruleSnapshot;

    /**
     * 接取任务时的奖励快照
     */
    private String prizeSnapshot;

    /**
     * 达标时间
     */
    private LocalDateTime completeTime;

    /**
     * 创建人
     */
    private String createBy;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新人
     */
    private String updateBy;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;

}
