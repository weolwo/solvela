package solvela.prize;

import solvela.enums.EnableStatusEnum;
import solvela.enums.ApproveModeEnum;
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
     * 活动编码
     */
    private String activityCode;

    /**
     * 优惠配置ID，关联 {@code t_promotion_config}，承载预算 / 库存 / 风控频次 / 审批阈值。
     *
     * <p><b>可空，且只对 {@code prizeType = MARKER} 可空</b>：标记类奖品不动账也不进提案，
     * 这四样东西对它一个都不适用，{@code MarkerHandler} 从头到尾不会读这一列。
     * 其余类型必填 —— 这条规则由 {@code PrizeConfigService.checkPromotionConfigMatch}
     * 按类型判，不是靠 Form 上的 {@code @NotNull}（那里看不到 prizeType）。
     */
    private Long promotionConfigId;

    /**
     * 资产类型：SCORE, BALANCE, COUPON, PHYSICAL, MARKER, LOTTERY, CUSTOM
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
     * 审批模式：0-自动免审, 1-人工审批
     */
    private ApproveModeEnum approveMode;

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
    private EnableStatusEnum status;

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
