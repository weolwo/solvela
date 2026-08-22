package sa.mall.commodity.domain.form;

import sa.base.common.domain.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 商城-商品主表 分页查询表单
 *
 * @Author weolwo
 * @Date 2026-08-22 19:29:59
 * @Copyright weolwo
 */

@Data
@EqualsAndHashCode(callSuper = false)
public class MallCommodityQueryForm extends PageParam {

    @Schema(description = "商品编码：10位大写字母+数字，全局唯一，创建后不可改")
    private String commodityCode;

    @Schema(description = "商品类型：PHYSICAL-实物(走tPhysicalDelivery), COUPON-优惠券(走tMemberCoupon), BALANCE-现金/红包(走钱包入账)")
    private String commodityType;

    @Schema(description = "商品名称")
    private String commodityName;

    @Schema(description = "支付方式：1-纯积分, 2-积分+现金")
    private Integer payType;

}
