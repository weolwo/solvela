package sa.prize.prizelog.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 奖励记录表 实体类
 *
 * @Author weolwo
 * @Date 2026-04-18 20:27:03
 * @Copyright weolwo
 */

@Data
@TableName("t_prize_log")
public class PrizeLog {

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
     * 奖品编码
     */
    private String prizeCode;

    /**
     * 活动编码
     */
    private String activityCode;

    /**
     * 奖品级别
     */
    private Integer prizeLevel;

    /**
     * 奖品名称
     */
    private String prizeName;

    /**
     * 奖励类型：SCORE, BALANCE, COUPON, PHYSICAL
     */
    private String prizeType;

    /**
     * 奖励体值(积分数/券ID)
     */
    private String prizeValue;

    /**
     * 异常原因
     */
    private String failReason;

    /**
     * 审批状态：0-无需审批, 1-待审批, 2-已批准, 3-已驳回
     */
    private Integer approveStatus;

    /**
     * 审批人
     */
    private String approveBy;

    /**
     * 审批时间
     */
    private LocalDateTime approveTime;

    /**
     * 过期时间
     */
    private LocalDateTime validUntil;

    /**
     * 执行状态：0-等待, 1-成功, 2-失败
     */
    private Integer status;

    /**
     * 外部单号
     */
    private String externalBizNo;

    /**
     * 异常原因
     */
    private String remark;

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
