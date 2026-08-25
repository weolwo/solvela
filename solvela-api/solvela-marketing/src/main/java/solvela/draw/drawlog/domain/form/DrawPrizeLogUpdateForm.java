package solvela.draw.drawlog.domain.form;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 抽奖记录 更新表单
 *
 * @Author weolwo
 * @Date 2026-04-19 09:21:26
 * @Copyright weolwo
 */

@Data
public class DrawPrizeLogUpdateForm {

    @Schema(description = "id", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "id 不能为空")
    private Long id;

    @Schema(description = "状态: 0-未中奖, 1-已中奖, 2-库存不足, 3-异常")
    private Integer status;

    @Schema(description = "备注")
    private String remark;

}