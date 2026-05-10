package net.lab1024.sa.prize.prizeconfig.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 奖品配置表 列表VO
 *
 * @Author weolwo
 * @Date 2026-04-18 20:20:44
 * @Copyright weolwo
 */

@Data
public class PrizeConfigVO {


    @Schema(description = "id")
    private Long id;

    @Schema(description = "租户ID")
    private String tenantId;

    @Schema(description = "活动编码")
    private String activityCode;

    @Schema(description = "优惠配置ID")
    private Long promotionConfigId;

    @Schema(description = "资产类型：SCORE, BALANCE, COUPON, PHYSICAL, LOTTERY, CUSTOM")
    private String prizeType;

    @Schema(description = "奖品名称")
    private String prizeName;

    @Schema(description = "奖品编码")
    private String prizeCode;

    @Schema(description = "奖品级别")
    private Integer prizeLevel;

    @Schema(description = "奖励价值")
    private BigDecimal prizeValue;

    @Schema(description = "排序权重")
    private Integer sortWeight;

    @Schema(description = "扩展信息：如奖品图片URL、跳转链接等")
    private String ext;

    @Schema(description = "状态：0-停用, 1-启用")
    private Integer status;

    @Schema(description = "创建人")
    private String createBy;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    @Schema(description = "更新人")
    private String updateBy;

    @Schema(description = "更新时间")
    private LocalDateTime updateTime;

}
