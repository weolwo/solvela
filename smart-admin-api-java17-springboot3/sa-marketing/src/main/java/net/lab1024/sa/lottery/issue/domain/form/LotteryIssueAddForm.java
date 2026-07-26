package net.lab1024.sa.lottery.issue.domain.form;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 期号配置 新建表单
 *
 * @Author weolwo
 * @Date 2026-05-09 16:54:51
 * @Copyright weolwo
 */

@Data
public class LotteryIssueAddForm {

    @Schema(description = "租户id", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "租户id 不能为空")
    private String tenantId;

    @Schema(description = "彩票编码", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "彩票编码 不能为空")
    private String lotteryCode;

    @Schema(description = "期号", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "期号 不能为空")
    private String issueNo;

    @Schema(description = "已售/已派发数量", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "已售/已派发数量 不能为空")
    private Integer soldCount;

    @Schema(description = "售卖开始时间", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "售卖开始时间 不能为空")
    private LocalDateTime saleStartTime;

    @Schema(description = "售卖结束时间", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "售卖结束时间 不能为空")
    private LocalDateTime saleEndTime;

    @Schema(description = "开奖时间")
    private LocalDateTime settleTime;

    @Schema(description = "开奖号码")
    private String winningNumber;

    @Schema(description = "状态: 0-待开奖, 1-售卖中, 2-已开奖", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "状态: 0-待开奖, 1-售卖中, 2-已开奖 不能为空")
    private Integer status;

    @Schema(description = "创建人")
    private String createBy;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

}