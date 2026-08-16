package sa.ledger.wallet.domain.form;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import lombok.Data;

/**
 * 会员钱包表 更新表单
 *
 * @Author weolwo
 * @Date 2026-04-18 23:56:48
 * @Copyright weolwo
 */

@Data
public class MemberWalletUpdateForm {

    @Schema(description = "id", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "id 不能为空")
    private Long id;

    @Schema(description = "资产类型：SCORE-积分, BALANCE-现金")
    private String assetType;

    @Schema(description = "余额")
    private BigDecimal balance;

    @Schema(description = "状态：0-冻结, 1-正常")
    private Integer status;

    @Schema(description = "乐观锁版本号")
    private Integer version;

}