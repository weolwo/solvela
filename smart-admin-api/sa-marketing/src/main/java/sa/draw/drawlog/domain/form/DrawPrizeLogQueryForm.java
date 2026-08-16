package sa.draw.drawlog.domain.form;

import sa.base.common.domain.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 抽奖记录 分页查询表单
 *
 * @Author weolwo
 * @Date 2026-04-19 09:21:26
 * @Copyright weolwo
 */

@Data
@EqualsAndHashCode(callSuper = false)
public class DrawPrizeLogQueryForm extends PageParam {

    @Schema(description = "租户id")
    private String tenantId;

    @Schema(description = "请求ID")
    private String traceId;

    @Schema(description = "活动编码")
    private String activityCode;

    @Schema(description = "创建时间")
    private LocalDate createTimeBegin;

    @Schema(description = "创建时间")
    private LocalDate createTimeEnd;

    @Schema(description = "会员名")
    private String memberName;

    @Schema(description = "奖池编码")
    private String poolCode;

    @Schema(description = "状态: 0-未中奖, 1-已中奖, 2-库存不足, 3-异常")
    private Integer status;

}
