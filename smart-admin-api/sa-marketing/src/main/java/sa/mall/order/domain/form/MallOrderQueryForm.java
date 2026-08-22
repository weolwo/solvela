package sa.mall.order.domain.form;

import sa.base.common.domain.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 商城-兑换订单 分页查询表单
 *
 * @Author weolwo
 * @Date 2026-08-22 19:35:46
 * @Copyright weolwo
 */

@Data
@EqualsAndHashCode(callSuper = false)
public class MallOrderQueryForm extends PageParam {

    @Schema(description = "订单号：服务端生成，对外唯一标识，同时作为扣积分的幂等键")
    private String orderNo;

    @Schema(description = "下单时的会员账号【展示快照，非关联键，不要用于查询】")
    private String memberName;

    @Schema(description = "商品编码（跨环境稳定的那个）")
    private String commodityCode;

    @Schema(description = "商品类型快照：PHYSICAL / COUPON / BALANCE，履约分派靠它")
    private String commodityType;

    @Schema(description = "状态：0-待支付, 10-待履约, 20-履约中, 30-已完成, 40-已取消, 50-已退款, 60-履约失败")
    private Integer status;

}
