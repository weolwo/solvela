package solvela.admin.module.draw.poolconfig.domain.vo;

import solvela.enums.PrizePoolStatusEnum;
import solvela.enums.DrawModeEnum;
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

    @Schema(description = "活动编码")
    private String activityCode;

    @Schema(description = "奖池唯一编码 (如: VIP_POOL)")
    private String poolCode;

    @Schema(description = "奖池名称")
    private String poolName;

    @Schema(description = "0关闭，1开启")
    private PrizePoolStatusEnum status;

    @Schema(description = "创建人")
    private String createBy;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    @Schema(description = "更新人")
    private String updateBy;

    @Schema(description = "更新时间")
    private LocalDateTime updateTime;

}
