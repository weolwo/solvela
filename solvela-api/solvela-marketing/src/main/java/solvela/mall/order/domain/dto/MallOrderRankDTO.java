package solvela.mall.order.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 兑换商品排行的一行。
 *
 * <p>按<b>兑换件数</b>排而不是订单数：一单可以兑多件，按订单数排会让「一单兑 10 件」
 * 的商品排在「10 单各兑 1 件」的后面 —— 而消耗掉的库存是一样的，运营关心的是后者。
 *
 * @Date 2026-08-23
 */
@Data
public class MallOrderRankDTO {

    @Schema(description = "商品id")
    private Long commodityId;

    /** 商品名取的是<b>订单里的快照</b>：商品改名后，历史订单该显示当时那个名字 */
    @Schema(description = "商品名称（下单时的快照）")
    private String commodityName;

    @Schema(description = "商品编码")
    private String commodityCode;

    @Schema(description = "兑换件数")
    private Long quantitySum;

    @Schema(description = "订单数")
    private Long orderCount;

    @Schema(description = "兑换人数（按会员号去重）")
    private Long memberCount;

    @Schema(description = "消耗积分合计")
    private Long payPointsSum;
}
