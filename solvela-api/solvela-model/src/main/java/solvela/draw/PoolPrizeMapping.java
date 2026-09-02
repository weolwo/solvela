package solvela.draw;

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
     * 奖池编码
     */
    private String poolCode;

    /**
     * 奖项id
     */
    private Long prizeItemId;

    /**
     * 中奖概率，单位<b>百分比</b>：{@code 10.9500} 表示 10.95%。同一奖池所有坑位之和须为 100。
     *
     * <p>⚠️ 这里原本写的是「万分位」，<b>是错的</b> —— 闭环校验对着 100 做、
     * 后台报错文案写「必须等于100%」、前端也按 {@code %} 渲染。建表注释同样写错，已一并订正。
     *
     * <p>运行态不用这个类型：{@code DrawSlot.ofPercent} 会在组装奖池快照时换算成
     * {@code Ppm}（百万分之一整数），引擎内部不存在小数。列是 {@code decimal(8,4)}，
     * 最小步长 0.0001% 正好是百万分之一，换算无损。
     */
    private BigDecimal probability;

    /**
     * 是否兜底奖项：1-兜底(库存不足时降级命中)，每池最多一个
     */
    private Boolean isFallback;

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
