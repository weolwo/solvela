package solvela.admin.module.draw.poolitem.domain.form;

import solvela.base.domain.PageParam;
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

    @Schema(description = "活动编码")
    private String activityCode;

    /**
     * 只看有体检告警的奖项。库存口径漂移、已超发、快抽空都归在这里 ——
     * 都是要立刻处理、而看裸数字看不出来的情况。
     */
    @Schema(description = "只看有体检告警的奖项")
    private Boolean onlyIssue;

}
