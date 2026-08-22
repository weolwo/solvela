package sa.ledger.logistic.domain.form;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 发货物流表 更新表单
 *
 * @Author weolwo
 * @Date 2026-04-19 00:03:01
 * @Copyright weolwo
 */

@Data
public class PhysicalDeliveryUpdateForm {

    @Schema(description = "id", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "id 不能为空")
    private Long id;

    /*
     * 🔴 下面三处 @Size 不是 UI 层面的挑剔，是<b>密文列宽的硬约束</b>。
     * 三列都加密落库，密文长度 = 3(前缀) + base64(12 + 明文字节数 + 16)：
     *     姓名 40 字符(120B) -> 203  ≤ varchar(255)
     *     电话 30 字符( 30B) ->  83  ≤ varchar(255)
     *     地址 100 字符(300B) -> 443 ≤ varchar(512)
     * 放开上限而不改列宽，后果是 MySQL 非严格模式<b>静默截断密文</b> ——
     * 表现为「存进去了，读出来解密失败」，而且那一行救不回来。
     * 改这里或改列宽时，两边一起改，算式见 PiiCipher.cipherTextLength。
     */
    @Schema(description = "收件人姓名（密文落库）", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "收件人姓名 不能为空")
    @Size(max = 40, message = "收件人姓名 最多 40 个字")
    private String receiverName;

    @Schema(description = "收件人电话（密文落库）", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "收件人电话 不能为空")
    @Size(max = 30, message = "收件人电话 最多 30 位")
    private String receiverPhone;

    @Schema(description = "收件详细地址（密文落库）", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "收件详细地址 不能为空")
    @Size(max = 100, message = "收件详细地址 最多 100 个字")
    private String receiverAddress;

    @Schema(description = "物流公司")
    private String logisticsCompany;

    @Schema(description = "物流单号")
    private String logisticsNo;

    @Schema(description = "状态：0-待发货, 1-已发货, 2-已签收, 3-异常退回")
    private Integer status;

}