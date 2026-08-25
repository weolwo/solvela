package solvela.lottery.numberpool.domain.form;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 彩票号码池 新建表单
 *
 * @Author weolwo
 * @Date 2026-04-19 11:31:09
 * @Copyright weolwo
 */

@Data
public class LotteryNumberPoolAddForm {

    @Schema(description = "彩票编码", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "彩票编码 不能为空")
    private String lotteryCode;

    @Schema(description = "彩票号码", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "彩票号码 不能为空")
    private String ticketNumber;

    @Schema(description = "发号序列号", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "发号序列号 不能为空")
    private Integer sequenceNo;

}