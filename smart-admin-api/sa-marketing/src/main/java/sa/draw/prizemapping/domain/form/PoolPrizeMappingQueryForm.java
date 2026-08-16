package sa.draw.prizemapping.domain.form;

import io.swagger.v3.oas.annotations.media.Schema;
import sa.base.common.domain.PageParam;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 奖池奖项映射 分页查询表单
 *
 * @Author weolwo
 * @Date 2026-04-19 10:07:03
 * @Copyright weolwo
 */

@Data
@EqualsAndHashCode(callSuper = false)
public class PoolPrizeMappingQueryForm extends PageParam {

    /**
     * 活动编码。页面先按活动收窄，再在活动内选具体奖池 ——
     * 奖池编码是十位随机码，脱离活动没人认得出是哪个池。
     */
    @Schema(description = "活动编码")
    private String activityCode;

    @Schema(description = "奖池编码")
    private String poolCode;

    /**
     * 只看有体检告警的奖池。这是本页最主要的巡检入口 ——
     * 概率未闭环的奖池不是「配置可疑」，而是按下抽奖就报错，必须能一眼筛出来。
     */
    @Schema(description = "只看有体检告警的奖池")
    private Boolean onlyIssue;
}
