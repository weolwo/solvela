package sa.draw.poolconfig.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 奖池配置 实体类
 *
 * @Author weolwo
 * @Date 2026-04-19 09:42:12
 * @Copyright weolwo
 */

@Data
@TableName("t_prize_pool_config")
public class PrizePoolConfig {

    /**
     * id
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 活动编码
     */
    private String activityCode;

    /**
     * 奖池唯一编码 (如: VIP_POOL)
     */
    private String poolCode;

    /**
     * 奖池名称
     */
    private String poolName;

    /**
     * 重置周期，天，周，月，活动期间
     */
    private String resetPeriod;

    /**
     * 抽奖算法: 1-按概率(probability), 2-按库存比例(stock_ratio)
     */
    private Integer drawMode;

    /**
     * 进入该奖池的前置脚本
     */
    private String scriptId;

    /**
     * 0关闭，1开启
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
