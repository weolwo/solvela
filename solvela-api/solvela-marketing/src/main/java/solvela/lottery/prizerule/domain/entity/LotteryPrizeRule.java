package solvela.lottery.prizerule.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 彩票奖励配置 实体类
 *
 * @Author weolwo
 * @Date 2026-04-19 11:50:34
 * @Copyright weolwo
 */

@Data
@TableName("t_lottery_prize_rule")
public class LotteryPrizeRule {

    /**
     * 主键id
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 彩票编码
     */
    private String lotteryCode;

    /**
     * 奖品奖级
     */
    private Integer prizeLevel;

    /**
     * 匹配规则,EXACT:全号, TAIL:尾号匹配, HEAD:首号匹配
     */
    private String matchRule;

    /**
     * 匹配长度
     */
    private Integer matchLength;

    /**
     * 奖品编码
     */
    private String prizeCode;

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
