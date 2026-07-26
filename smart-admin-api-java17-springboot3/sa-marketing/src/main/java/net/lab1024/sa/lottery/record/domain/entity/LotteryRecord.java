package net.lab1024.sa.lottery.record.domain.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 用户号码记录 实体类
 *
 * @Author weolwo
 * @Date 2026-04-19 11:57:08
 * @Copyright weolwo
 */

@Data
@TableName("t_lottery_record")
public class LotteryRecord {

    /**
     * 主键id
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 租户id
     */
    private String tenantId;

    /**
     * 彩票编码
     */
    private String lotteryCode;

    /**
     * 期号
     */
    private String issueNo;

    /**
     * FPE算号基数
     */
    private Integer sequenceNo;

    /**
     * 彩票号码
     */
    private String ticketNumber;

    /**
     * 会员唯一标识
     */
    private String memberName;

    /**
     * 领号时间
     */
    private LocalDateTime obtainTime;

    /**
     * 中奖状态: 0-未开奖, 1-未中奖, 2-已中奖
     */
    private Integer winStatus;

    /**
     * 奖励等级
     */
    private Integer prizeLevel;

    /**
     * 防篡改签名
     */
    private String securitySign;

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
