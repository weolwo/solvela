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
     * 已售/已派发数量
     */
    private Integer soldCount;

    /**
     * 售卖开始时间
     */
    private LocalDateTime saleStartTime;

    /**
     * 售卖结束时间
     */
    private LocalDateTime saleEndTime;

    /**
     * 开奖时间
     */
    private LocalDateTime settleTime;

    /**
     * 开奖号码
     */
    private String winningNumber;

    /**
     * 状态: 0-待开奖, 1-部分开奖, 2-已开奖
     */
    private Integer status;

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
