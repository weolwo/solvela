package net.lab1024.sa.prize.prizelog.domain.form;

import net.lab1024.sa.base.common.domain.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 奖励记录表 分页查询表单
 *
 * @Author weolwo
 * @Date 2026-04-18 20:27:03
 * @Copyright weolwo
 */

@Data
@EqualsAndHashCode(callSuper = false)
public class PrizeLogQueryForm extends PageParam {

    @Schema(description = "租户ID")
    private String tenantId;

    @Schema(description = "会员名")
    private String memberName;

    @Schema(description = "奖品编码")
    private String prizeCode;

    @Schema(description = "活动编码")
    private String activityCode;

    @Schema(description = "创建时间")
    private LocalDate createTimeBegin;

    @Schema(description = "创建时间")
    private LocalDate createTimeEnd;

}
