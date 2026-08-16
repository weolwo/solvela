package sa.draw.prizemapping.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 奖池奖项映射 实体类
 *
 * @Author weolwo
 * @Date 2026-04-19 10:07:03
 * @Copyright weolwo
 */

@Data
@TableName("t_pool_prize_mapping")
public class PoolPrizeMapping {

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
     * 奖池编码
     */
    private String poolCode;

    /**
     * 奖项id
     */
    private Long prizeItemId;

    /**
     * 中奖概率(万分位)
     */
    private BigDecimal probability;

    /**
     * 是否兜底奖项：1-兜底(库存不足时降级命中)，每池最多一个
     */
    private Integer isFallback;

    /**
     * 序号
     */
    private Integer sortWeight;

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
