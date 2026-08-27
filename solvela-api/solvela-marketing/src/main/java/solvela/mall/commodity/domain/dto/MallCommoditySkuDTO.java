package solvela.mall.commodity.domain.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.Map;

/**
 * 商品详情里的一行 SKU。
 *
 * <p>与 {@code MallSkuDTO}（SKU 列表页用）的差别：这里的 {@code skuAttrs} 已经从 JSON 串
 * 解析成 map，前端拿到就能渲染成规格列，不用各处自己 JSON.parse 一遍。
 *
 * @Date 2026-08-23
 */
@Data
public class MallCommoditySkuDTO {

    /** SKU id */
    private Long id;

    /** SKU编码：服务端生成，只读 */
    private String skuCode;

    /** 规格组合，已解析成键值对 */
    private Map<String, String> skuAttrs;

    /** 该规格专属图 file_id */
    private Long skuCoverFileId;

    /** 本规格所需积分：为空表示继承商品基准价 */
    private Integer skuPointsPrice;

    /** 本规格所需现金：为空表示继承商品基准价 */
    private BigDecimal skuCashPrice;

    /** 总库存：运营投放量 */
    private Integer totalStock;

    /** 只读。展示成「投 100 / 售 37 / 锁 2 / 余 61」，运营才看得懂库存去哪了 */
    private Integer lockedStock;

    /** 已售数量：履约成功累加（只读） */
    private Integer soldCount;

    /** 可用库存 = 总库存 - 锁定 - 已售（数据库虚拟列，只读） */
    private Integer availableStock;

    /** 状态：0-停用, 1-启用 */
    private Integer skuStatus;

    /** 排序 */
    private Integer sort;
}
