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

    @Schema(description = "开始偏移量", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "开始偏移量 不能为空")
    private Integer startOffset;

    @Schema(description = "已售/已派发数量", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "已售/已派发数量 不能为空")
    private Integer soldCount;

    @Schema(description = "开始售卖", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "开始售卖 不能为空")
    private LocalDateTime sellStartTime;

    @Schema(description = "结束时间", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "结束时间 不能为空")
    private LocalDateTime sellEndTime;

    @Schema(description = "开奖时间")
    private LocalDateTime openTime;

    @Schema(description = "是否可重复开奖：0否，1是")
    private Integer canRepeat;

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