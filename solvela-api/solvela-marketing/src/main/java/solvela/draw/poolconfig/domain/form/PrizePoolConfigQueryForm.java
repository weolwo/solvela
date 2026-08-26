package solvela.draw.poolconfig.domain.form;

import solvela.base.domain.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 奖池配置 分页查询表单
 *
 * @Author weolwo
 * @Date 2026-04-19 09:42:12
 * @Copyright weolwo
 */

@Data
@EqualsAndHashCode(callSuper = false)
public class PrizePoolConfigQueryForm extends PageParam {

    @Schema(description = "活动编码")
    private String activityCode;

    @Schema(description = "奖池唯一编码 (如: VIPPOOL)")
    private String poolCode;

    @Schema(description = "奖池名称")
    private String poolName;

    @Schema(description = "0关闭，1开启")
    private Integer status;

    /**
     * 只看有体检告警的奖池。「配了却抽不了」是这页最该先处理的一类问题 ——
     * 概率没闭环、没配坑位、活动上线了池却关着，看裸字段一个都看不出来。
     */
    @Schema(description = "只看有体检告警的奖池")
    private Boolean onlyIssue;

    @Schema(description = "创建时间")
    private LocalDate createTimeBegin;

    @Schema(description = "创建时间")
    private LocalDate createTimeEnd;

}
