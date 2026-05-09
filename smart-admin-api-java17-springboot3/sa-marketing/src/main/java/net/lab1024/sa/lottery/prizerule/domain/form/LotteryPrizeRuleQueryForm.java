package net.lab1024.sa.lottery.prizerule.domain.form;

import net.lab1024.sa.base.common.domain.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 彩票奖励配置 分页查询表单
 *
 * @Author weolwo
 * @Date 2026-04-19 11:50:34
 * @Copyright weolwo
 */

@Data
@EqualsAndHashCode(callSuper = false)
public class LotteryPrizeRuleQueryForm extends PageParam {

    @Schema(description = "租户id")
    private String tenantId;

    @Schema(description = "彩票编码")
    private String lotteryCode;

    @Schema(description = "奖励等级")
    private String prizeLevel;

    @Schema(description = "创建时间")
    private LocalDate createTimeBegin;

    @Schema(description = "创建时间")
    private LocalDate createTimeEnd;

}
