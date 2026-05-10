package net.lab1024.sa.prize.prizelog.domain.form;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 奖励记录表 更新表单
 *
 * @Author weolwo
 * @Date 2026-04-18 20:27:03
 * @Copyright weolwo
 */

@Data
public class PrizeLogUpdateForm {

    @Schema(description = "id", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "id 不能为空")
    private Long id;

    @Schema(description = "异常原因")
    private String failReason;

    @Schema(description = "执行状态：0-等待, 1-成功, 2-失败")
    private Integer status;

    @Schema(description = "外部单号")
    private String externalBizNo;

    @Schema(description = "异常原因")
    private String remark;

}