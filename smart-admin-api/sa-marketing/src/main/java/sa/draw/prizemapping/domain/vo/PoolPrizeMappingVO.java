package sa.draw.prizemapping.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 奖池奖项映射 列表VO
 *
 * @Author weolwo
 * @Date 2026-04-19 10:07:03
 * @Copyright weolwo
 */

@Data
public class PoolPrizeMappingVO {


    @Schema(description = "id")
    private Long id;

    @Schema(description = "租户id")
    private String tenantId;

    @Schema(description = "奖池编码")
    private String poolCode;

    @Schema(description = "奖项id")
    private Long prizeItemId;

    @Schema(description = "中奖概率(万分位)")
    private BigDecimal probability;

    @Schema(description = "是否兜底奖项：1-兜底，每池最多一个")
    private Integer isFallback;

    @Schema(description = "序号")
    private Integer sortWeight;

    @Schema(description = "创建人")
    private String createBy;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    @Schema(description = "更新人")
    private String updateBy;

    @Schema(description = "更新时间")
    private LocalDateTime updateTime;

}
