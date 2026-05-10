package net.lab1024.sa.prize.prizelog.domain.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

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
     * 优惠配置ID
     */
    private Long promotionConfigId;

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
