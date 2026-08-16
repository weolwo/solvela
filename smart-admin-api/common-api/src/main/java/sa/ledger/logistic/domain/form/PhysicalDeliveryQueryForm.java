package sa.ledger.logistic.domain.form;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import sa.base.common.domain.PageParam;

import java.time.LocalDate;

/**
 * 发货物流表 分页查询表单
 *
 * @Author weolwo
 * @Date 2026-04-19 00:03:01
 * @Copyright weolwo
 */

@Data
@EqualsAndHashCode(callSuper = false)
public class PhysicalDeliveryQueryForm extends PageParam {

    @Schema(description = "会员名")
    private String memberName;

    @Schema(description = "发奖提案ID")
    private Long proposalId;

    @Schema(description = "来源类型")
    private String sourceType;

    @Schema(description = "物流单号")
    private String logisticsNo;

    @Schema(description = "状态：0-待发货, 1-已发货, 2-已签收, 3-异常退回")
    private Integer status;

    @Schema(description = "创建时间")
    private LocalDate createTimeBegin;

    @Schema(description = "创建时间")
    private LocalDate createTimeEnd;

}
