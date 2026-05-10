package net.lab1024.sa.risk.proposal.domain.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 提案表 实体类
 *
 * @Author weolwo
 * @Date 2026-04-18 23:13:50
 * @Copyright weolwo
 */

@Data
@TableName("t_proposal_record")
public class ProposalRecord {

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
     * 来源：TASK(任务), DRAW(抽奖), MANUAL(人工)
     */
    private String sourceType;

    /**
     * 来源单号(task_record_id 或 draw_log_trace_id)
     */
    private String sourceBizId;

    /**
     * 优惠配置ID
     */
    private Long promotionConfigId;

    /**
     * 优惠金额
     */
    private BigDecimal promotionValue;

    /**
     * 状态：0-等待中, 10-待一审, 11-待二审, 20-驳回, 30-待执行, 40-执行中, 50-成功, 60-部分成功, 70-彻底失败, 80-风控拦截
     */
    private Integer status;

    /**
     * 执行失败/风控拦截原因
     */
    private String remark;

    /**
     * 一审人
     */
    private String firstReviewer;

    /**
     * 一审时间
     */
    private LocalDateTime firstReviewTime;

    /**
     * 二审人
     */
    private String secondReviewer;

    /**
     * 二审时间
     */
    private LocalDateTime secondReviewTime;

    /**
     * 审核意见/驳回理由
     */
    private String reviewComment;

    /**
     * 创建人
     */
    private String createBy;

    /**
     * 创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /**
     * 更新人
     */
    private String updateBy;

    /**
     * 更新时间
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

}
