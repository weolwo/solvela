package sa.mall.order.domain.form;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import lombok.Data;

/**
 * 商城-兑换订单 新建表单
 *
 * @Author weolwo
 * @Date 2026-08-22 19:35:46
 * @Copyright weolwo
 */

@Data
public class MallOrderAddForm {

    @Schema(description = "id", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "id 不能为空")
    private Long id;

    @Schema(description = "订单号：服务端生成，对外唯一标识，同时作为扣积分的幂等键", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "订单号：服务端生成，对外唯一标识，同时作为扣积分的幂等键 不能为空")
    private String orderNo;

    @Schema(description = "会员号：关联键", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "会员号：关联键 不能为空")
    private Long memberId;

    @Schema(description = "商品id", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "商品id 不能为空")
    private Long commodityId;

    @Schema(description = "商品编码（跨环境稳定的那个）", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "商品编码（跨环境稳定的那个） 不能为空")
    private String commodityCode;

    @Schema(description = "SKUid", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "SKUid 不能为空")
    private Long skuId;

    @Schema(description = "SKU编码", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "SKU编码 不能为空")
    private String skuCode;

    @Schema(description = "商品类型快照：PHYSICAL / COUPON / BALANCE，履约分派靠它", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "商品类型快照：PHYSICAL / COUPON / BALANCE，履约分派靠它 不能为空")
    private String commodityType;

    @Schema(description = "商品名称快照", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "商品名称快照 不能为空")
    private String commodityName;

    @Schema(description = "规格快照", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "规格快照 不能为空")
    private String skuAttrs;

    @Schema(description = "兑换件数", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "兑换件数 不能为空")
    private Integer quantity;

    @Schema(description = "单件积分单价快照", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "单件积分单价快照 不能为空")
    private Integer pointsPrice;

    @Schema(description = "单件现金单价快照", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "单件现金单价快照 不能为空")
    private BigDecimal cashPrice;

    @Schema(description = "实付积分合计", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "实付积分合计 不能为空")
    private Integer payPoints;

    @Schema(description = "实付现金合计", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "实付现金合计 不能为空")
    private BigDecimal payCash;

    @Schema(description = "状态：0-待支付, 10-待履约, 20-履约中, 30-已完成, 40-已取消, 50-已退款, 60-履约失败", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "状态：0-待支付, 10-待履约, 20-履约中, 30-已完成, 40-已取消, 50-已退款, 60-履约失败 不能为空")
    private Integer status;

    @Schema(description = "订单来源：NORMAL-日常兑换, FLASH_SALE-限时抢购场次", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "订单来源：NORMAL-日常兑换, FLASH_SALE-限时抢购场次 不能为空")
    private String sourceType;

}