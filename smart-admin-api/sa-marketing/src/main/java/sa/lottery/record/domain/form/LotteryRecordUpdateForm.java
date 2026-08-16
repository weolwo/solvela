package sa.lottery.record.domain.form;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 用户号码记录 更新表单
 *
 * @Author weolwo
 * @Date 2026-04-19 11:57:08
 * @Copyright weolwo
 */

@Data
public class LotteryRecordUpdateForm {

    @Schema(description = "id", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "id 不能为空")
    private Long id;

    @Schema(description = "中奖状态: 0-未开奖, 1-未中奖, 已开奖", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "中奖状态: 0-未开奖, 1-未中奖, 已开奖 不能为空")
    private Integer winStatus;

    @Schema(description = "奖励等级")
    private Integer prizeLevel;

}