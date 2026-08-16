package sa.activity.domain.form;

import sa.base.common.domain.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 活动配置 分页查询表单
 *
 * @Author weolwo
 * @Date 2026-04-18 19:31:49
 * @Copyright weolwo
 */

@Data
@EqualsAndHashCode(callSuper = false)
public class ActivityConfigQueryForm extends PageParam {

    @Schema(description = "租户id")
    private String tenantId;

    @Schema(description = "活动编码")
    private String activityCode;

    @Schema(description = "活动名称")
    private String activityName;

    @Schema(description = "创建时间")
    private LocalDate createTimeBegin;

    @Schema(description = "创建时间")
    private LocalDate createTimeEnd;

    @Schema(description = "活动类型：BASIC / DRAW / TASK / LOTTERY")
    private String activityType;

    @Schema(description = "状态：0-未开始, 1-上线, 2-下线")
    private Integer status;

}
