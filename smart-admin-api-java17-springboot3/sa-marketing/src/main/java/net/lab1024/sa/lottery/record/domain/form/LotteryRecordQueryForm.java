package net.lab1024.sa.lottery.record.domain.form;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import net.lab1024.sa.base.common.domain.PageParam;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 用户号码记录 分页查询表单
 *
 * @Author weolwo
 * @Date 2026-04-19 11:57:08
 * @Copyright weolwo
 */

@Data
@EqualsAndHashCode(callSuper = false)
public class LotteryRecordQueryForm extends PageParam {

    @Schema(description = "租户id")
    private String tenantId;

    @Schema(description = "彩票编码")
    private String lotteryCode;

    @Schema(description = "归属期号")
    private String issueNo;

    @Schema(description = "彩票号码")
    private String ticketNumber;

    @Schema(description = "创建时间")
    private LocalDateTime createTimeBegin;

    @Schema(description = "创建时间")
    private LocalDateTime createTimeEnd;

    @Schema(description  = "中奖状态: 0-未开奖, 1-未中奖, 已开奖 错误")
    private Integer winStatus;

    @Schema(description = "会员名")
    private String memberName;

    @Schema(description = "奖励等级")
    private Integer prizeLevel;

}
