package net.lab1024.sa.lottery.config.domain.form;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 彩票配置 更新表单
 *
 * @Author weolwo
 * @Date 2026-04-19 11:16:39
 * @Copyright weolwo
 */

@Data
public class LotteryConfigUpdateForm {

    @Schema(description = "id", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "id 不能为空")
    private Long id;

    @Schema(description = "彩票名称", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "彩票名称 不能为空")
    private String lotteryName;

    @Schema(description = "状态：0-下线, 1-上线")
    private Integer status;

}