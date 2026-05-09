package net.lab1024.sa.lottery.issue.domain.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 期号配置 实体类
 *
 * @Author weolwo
 * @Date 2026-05-09 16:54:51
 * @Copyright weolwo
 */

@Data
@TableName("t_lottery_issue")
public class LotteryIssue {

    /**
     * id
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
     * 开始偏移量
     */
    private Integer startOffset;

    /**
     * 已售/已派发数量
     */
    private Integer soldCount;

    /**
     * 开始售卖
     */
    private LocalDateTime sellStartTime;

    /**
     * 结束时间
     */
    private LocalDateTime sellEndTime;

    /**
     * 开奖时间
     */
    private LocalDateTime openTime;

    /**
     * 是否可重复开奖：0否，1是
     */
    private Integer canRepeat;

    /**
     * 开奖号码
     */
    private String winningNumber;

    /**
     * 状态: 0-待开奖, 1-售卖中, 2-已开奖
     */
    private Integer status;

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
