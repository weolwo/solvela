package net.lab1024.sa.prize.prizeconfig.domain.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 奖品配置表 实体类
 *
 * @Author weolwo
 * @Date 2026-04-18 20:20:44
 * @Copyright weolwo
 */

@Data
@TableName("t_prize_config")
public class PrizeConfig {

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
     * 活动编码
     */
    private String activityCode;

    /**
     * 优惠配置ID
     */
    private Long promotionConfigId;

    /**
     * 资产类型：SCORE, BALANCE, COUPON, PHYSICAL, LOTTERY, CUSTOM
     */
    private String prizeType;

    /**
     * 奖品名称
     */
    private String prizeName;

    /**
     * 奖品编码
     */
    private String prizeCode;

    /**
     * 奖品级别
     */
    private Integer prizeLevel;

    /**
     * 奖励价值
     */
    private BigDecimal prizeValue;

    /**
     * 排序权重
     */
    private Integer sortWeight;

    /**
     * 扩展信息：如奖品图片URL、跳转链接等
     */
    private String ext;

    /**
     * 状态：0-停用, 1-启用
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
