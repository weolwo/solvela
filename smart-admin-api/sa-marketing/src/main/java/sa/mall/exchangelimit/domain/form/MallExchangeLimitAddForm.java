package sa.mall.exchangelimit.domain.form;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 商城-会员限兑计数 新建表单
 *
 * @Author weolwo
 * @Date 2026-08-22 19:33:25
 * @Copyright weolwo
 */

@Data
public class MallExchangeLimitAddForm {

    @Schema(description = "id", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "id 不能为空")
    private Long id;

    @Schema(description = "会员号：关联键", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "会员号：关联键 不能为空")
    private Long memberId;

    @Schema(description = "商品id", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "商品id 不能为空")
    private Long commodityId;

    @Schema(description = "周期标识：NONE(终身) / 20260819(日) / 2026W34(周) / 202608(月)。取值口径对齐 t_task_record.period_key", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "周期标识：NONE(终身) / 20260819(日) / 2026W34(周) / 202608(月)。取值口径对齐 t_task_record.period_key 不能为空")
    private String periodKey;

    @Schema(description = "该周期内已兑件数", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "该周期内已兑件数 不能为空")
    private Integer usedCount;

}