package sa.draw.poolconfig.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 奖池配置 列表VO
 *
 * @Author weolwo
 * @Date 2026-04-19 09:42:12
 * @Copyright weolwo
 */

@Data
public class PrizePoolConfigVO {


    @Schema(description = "id")
    private Long id;

    @Schema(description = "租户id")
    private String tenantId;

    @Schema(description = "活动编码")
    private String activityCode;

    @Schema(description = "奖池唯一编码 (如: VIP_POOL)")
    private String poolCode;

    @Schema(description = "奖池名称")
    private String poolName;

    @Schema(description = "重置周期，天，周，月，活动期间")
    private String resetPeriod;

    @Schema(description = "抽奖算法: 1-按概率(probability), 2-按库存比例(stock_ratio)")
    private Integer drawMode;

    @Schema(description = "进入该奖池的前置脚本")
    private String scriptId;

    @Schema(description = "0关闭，1开启")
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
