package net.lab1024.sa.lottery.issue.domain.form;

import net.lab1024.sa.base.common.domain.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 期号配置 分页查询表单
 *
 * @Author weolwo
 * @Date 2026-05-09 16:54:51
 * @Copyright weolwo
 */

@Data
@EqualsAndHashCode(callSuper = false)
public class LotteryIssueQueryForm extends PageParam {

    @Schema(description = "租户id")
    private String tenantId;

    @Schema(description = "彩票编码")
    private String lotteryCode;

    @Schema(description = "期号")
    private String issueNo;

    @Schema(description = "状态: 0-待开奖, 1-售卖中, 2-已开奖")
    private Integer status;

    @Schema(description = "创建时间")
    private LocalDate createTimeBegin;

    @Schema(description = "创建时间")
    private LocalDate createTimeEnd;

}
