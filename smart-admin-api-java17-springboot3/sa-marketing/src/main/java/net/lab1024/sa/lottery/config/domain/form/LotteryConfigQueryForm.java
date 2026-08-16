package net.lab1024.sa.lottery.config.domain.form;

import net.lab1024.sa.base.common.domain.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 彩票配置 分页查询表单
 *
 * @Author weolwo
 * @Date 2026-04-19 11:16:39
 * @Copyright weolwo
 */

@Data
@EqualsAndHashCode(callSuper = false)
public class LotteryConfigQueryForm extends PageParam {

    @Schema(description = "租户id")
    private String tenantId;

    @Schema(description = "活动编码")
    private String activityCode;

    @Schema(description = "彩票编码")
    private String lotteryCode;

    @Schema(description = "彩票名称")
    private String lotteryName;

    @Schema(description = "状态：0-下线, 1-上线")
    private Integer status;

    /**
     * 只看有体检告警的玩法。「已上线却没配奖级」「上线了但没有可领号的期号」
     * 这类问题看裸字段一个都看不出来，而它们的后果是用户当场领不到号。
     */
    @Schema(description = "只看有体检告警的玩法")
    private Boolean onlyIssue;

    @Schema(description = "创建时间")
    private LocalDate createTimeBegin;

    @Schema(description = "创建时间")
    private LocalDate createTimeEnd;

}
