package net.lab1024.sa.draw.poolitem.domain.form;

import net.lab1024.sa.base.common.domain.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 奖池奖项库 分页查询表单
 *
 * @Author weolwo
 * @Date 2026-04-19 09:52:45
 * @Copyright weolwo
 */

@Data
@EqualsAndHashCode(callSuper = false)
public class PrizePoolItemQueryForm extends PageParam {

    @Schema(description = "租户id")
    private String tenantId;

    @Schema(description = "活动编码")
    private String activityCode;

}
