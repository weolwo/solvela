package solvela.lottery;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

import lombok.Data;

/**
 * 彩票配置 实体类
 *
 * @Author weolwo
 * @Date 2026-04-19 11:16:39
 * @Copyright weolwo
 */

@Data
@TableName("t_lottery_config")
public class LotteryConfig {

    /**
     * 主键id
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 活动编码
     */
    private String activityCode;

    /**
     * 彩票编码
     */
    private String lotteryCode;

    /**
     * 彩票名称
     */
    private String lotteryName;

    /**
     * 发号字符集
     */
    private String numberCharset;

    /**
     * 号码长度
     */
    private Integer numberLength;

    /**
     * 单期发行总数上限
     */
    private Integer totalCount;

    /**
     * 状态：0-下线, 1-上线
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
