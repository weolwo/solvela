package solvela.mall.commodity.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Map;

/**
 * 商品详情里的一行 SKU。
 *
 * <p>与 {@code MallSkuVO}（SKU 列表页用）的差别：这里的 {@code skuAttrs} 已经从 JSON 串
 * 解析成 map，前端拿到就能渲染成规格列，不用各处自己 JSON.parse 一遍。
 *
 * @Date 2026-08-23
 */
@Data
public class MallCommoditySkuVO {

    @Schema(description = "SKU id")
    private Long id;

    @Schema(description = "SKU编码：服务端生成，只读")
    private String skuCode;

    @Schema(description = "规格组合，已解析成键值对")
    private Map<String, String> skuAttrs;

    @Schema(description = "该规格专属图 file_id")
    private Long skuCoverFileId;

    @Schema(description = "本规格所需积分：为空表示继承商品基准价")
    private Integer skuPointsPrice;

    @Schema(description = "本规格所需现金：为空表示继承商品基准价")
    private BigDecimal skuCashPrice;

    @Schema(description = "总库存：运营投放量")
    private Integer totalStock;

    /** 只读。展示成「投 100 / 售 37 / 锁 2 / 余 61」，运营才看得懂库存去哪了 */
    @Schema(description = "锁定库存：已下单未履约（只读）")
    private Integer lockedStock;

    @Schema(description = "已售数量：履约成功累加（只读）")
    private Integer soldCount;

    @Schema(description = "可用库存 = 总库存 - 锁定 - 已售（数据库虚拟列，只读）")
    private Integer availableStock;

    @Schema(description = "状态：0-停用, 1-启用")
    private Integer skuStatus;

    @Schema(description = "排序")
    private Integer sort;
}
