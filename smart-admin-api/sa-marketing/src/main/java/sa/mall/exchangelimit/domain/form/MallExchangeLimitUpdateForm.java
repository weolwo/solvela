package sa.mall.exchangelimit.domain.form;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 商城-会员限兑计数 更新表单
 *
 * @Author weolwo
 * @Date 2026-08-22 19:33:25
 * @Copyright weolwo
 */

@Data
public class MallExchangeLimitUpdateForm {

    @Schema(description = "id", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "id 不能为空")
    private Long id;

}