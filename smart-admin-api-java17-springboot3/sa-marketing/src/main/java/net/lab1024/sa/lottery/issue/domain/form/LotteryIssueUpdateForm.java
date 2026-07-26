package net.lab1024.sa.lottery.issue.domain.form;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 期号配置 更新表单
 *
 * @Author weolwo
 * @Date 2026-05-09 16:54:51
 * @Copyright weolwo
 */

@Data
public class LotteryIssueUpdateForm {

    @Schema(description = "id", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "id 不能为空")
    private Long id;

    @Schema(description = "开奖时间")
    private LocalDateTime settleTime;

    @Schema(description = "开奖号码")
    private String winningNumber;

    @Schema(description = "状态: 0-待开奖, 1-售卖中, 2-已开奖", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "状态: 0-待开奖, 1-售卖中, 2-已开奖 不能为空")
    private Integer status;

    @Schema(description = "更新人")
    private String updateBy;

    @Schema(description = "更新时间")
    private LocalDateTime updateTime;

}