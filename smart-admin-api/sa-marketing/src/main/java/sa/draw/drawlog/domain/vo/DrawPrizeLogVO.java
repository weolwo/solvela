package sa.draw.drawlog.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 抽奖记录 列表VO
 *
 * @Author weolwo
 * @Date 2026-04-19 09:21:26
 * @Copyright weolwo
 */

@Data
public class DrawPrizeLogVO {


    @Schema(description = "id")
    private Long id;

    @Schema(description = "租户id")
    private String tenantId;

    @Schema(description = "请求ID")
    private String traceId;

    @Schema(description = "活动编码")
    private String activityCode;

    @Schema(description = "奖池编码")
    private String poolCode;

    @Schema(description = "会员名")
    private String memberName;

    @Schema(description = "奖项ID")
    private Long prizeItemId;

    @Schema(description = "奖品code")
    private String prizeCode;

    @Schema(description = "状态: 0-未中奖, 1-已中奖, 2-库存不足, 3-异常")
    private Integer status;

    @Schema(description = "备注")
    private String remark;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

}
