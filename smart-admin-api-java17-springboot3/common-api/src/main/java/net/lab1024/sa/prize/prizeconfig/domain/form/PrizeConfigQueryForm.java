package net.lab1024.sa.prize.prizeconfig.domain.form;

import net.lab1024.sa.base.common.domain.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 奖品配置表 分页查询表单
 *
 * @Author weolwo
 * @Date 2026-04-18 20:20:44
 * @Copyright weolwo
 */

@Data
@EqualsAndHashCode(callSuper = false)
public class PrizeConfigQueryForm extends PageParam {

    @Schema(description = "租户ID")
    private String tenantId;

    @Schema(description = "活动编码")
    private String activityCode;

    @Schema(description = "奖品编码")
    private String prizeCode;

    @Schema(description = "奖品级别")
    private Integer prizeLevel;

}
