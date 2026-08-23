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

    @Schema(description = "分类id")
    private Long categoryId;

    @Schema(description = "状态：0-下架, 1-上架, 2-草稿")
    private Integer status;

    @Schema(description = "商品类型：PHYSICAL-实物, COUPON-优惠券, BALANCE-现金/红包")
    private String commodityType;

    /** 模糊匹配。列表页那个输入框是「搜索」，精确等于的话运营得把商品名一字不差地打出来 */
    @Schema(description = "商品名称：模糊匹配")
    private String commodityName;

    @Schema(description = "支付方式：1-纯积分, 2-积分+现金")
    private Integer payType;

}
