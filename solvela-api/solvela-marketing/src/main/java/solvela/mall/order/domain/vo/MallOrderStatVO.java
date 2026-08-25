package solvela.mall.order.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 兑换订单统计。
 *
 * <p><b>与列表共用同一套查询条件</b>：顶部筛选改了统计跟着变。两套条件的话，
 * 运营会看到「统计说 100 单，下面列表只有 3 条」，然后不知道该信哪个。
 *
 * @Date 2026-08-23
 */
@Data
public class MallOrderStatVO {

    @Schema(description = "订单数")
    private Long orderCount;

    /**
     * 兑换人数。<b>和订单数分开看</b> —— 一个人兑 20 单，订单数是 20、人数是 1，
     * 拿订单数当参与人数用会把活动效果算错一个量级。
     */
    @Schema(description = "兑换人数（按会员号去重）")
    private Long memberCount;

    @Schema(description = "兑换件数合计")
    private Long quantitySum;

    @Schema(description = "已完成订单数")
    private Long finishedCount;

    @Schema(description = "待履约 + 履约中订单数")
    private Long processingCount;

    @Schema(description = "已取消 + 已退款订单数")
    private Long cancelledCount;

    @Schema(description = "履约失败订单数：不为 0 就得有人去看")
    private Long failedCount;

    /**
     * 消耗积分 / 现金。
     *
     * <p>🔴 只统计<b>实际扣款成功</b>的订单（待支付、已取消的不算）——
     * 把待支付订单的金额算进来，得到的是「如果所有人都付了会花多少」，那不是消耗。
     */
    @Schema(description = "消耗积分合计（不含待支付与已取消）")
    private Long payPointsSum;

    @Schema(description = "消耗现金合计（不含待支付与已取消）")
    private BigDecimal payCashSum;

    @Schema(description = "兑换商品排行")
    private List<MallOrderRankVO> commodityRank = new ArrayList<>();
}
