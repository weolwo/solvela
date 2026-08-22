package sa.mall.address.domain.form;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 商城-会员收货地址簿 新建表单
 *
 * @Author weolwo
 * @Date 2026-08-22 19:25:03
 * @Copyright weolwo
 */

@Data
public class MallAddressAddForm {

    @Schema(description = "id", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "id 不能为空")
    private Long id;

    @Schema(description = "会员号：关联键", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "会员号：关联键 不能为空")
    private Long memberId;

    @Schema(description = "收件人姓名【密文】", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "收件人姓名【密文】 不能为空")
    private String receiverName;

    @Schema(description = "收件人电话【密文】", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "收件人电话【密文】 不能为空")
    private String receiverPhone;

    @Schema(description = "详细门牌地址【密文】", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "详细门牌地址【密文】 不能为空")
    private String detailAddress;

    @Schema(description = "是否默认地址：0-否, 1-是。设默认时先把该会员其余行置0", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "是否默认地址：0-否, 1-是。设默认时先把该会员其余行置0 不能为空")
    private Integer isDefault;

}