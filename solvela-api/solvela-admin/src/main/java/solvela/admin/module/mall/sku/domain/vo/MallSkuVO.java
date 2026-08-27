package solvela.admin.module.mall.sku.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 商城-SKU与库存 列表VO
 *
 * @Author weolwo
 * @Date 2026-08-22 19:37:50
 * @Copyright weolwo
 */

@Data
public class MallSkuVO {


    @Schema(description = "id")
    private Long id;

    @Schema(description = "关联 t_mall_commodity.id")
    private Long commodityId;

    // ---------- join 出来的商品信息：只看 SKU 编码认不出这是哪个商品的哪个规格 ----------

    @Schema(description = "商品名称")
    private String commodityName;

    @Schema(description = "商品编码")
    private String commodityCode;

    @Schema(description = "商品封面 file_id")
    private Long coverFileId;

    @Schema(description = "商品状态：0-下架, 1-上架, 2-草稿")
    private Integer commodityStatus;

    @Schema(description = "商品基准积分（SKU 未覆盖时的实际售价）")
    private Integer basePointsPrice;

    @Schema(description = "分类id")
    private Long categoryId;

    @Schema(description = "分类名称")
    private String categoryName;

    @Schema(description = "SKU编码：10位大写字母+数字，全局唯一")
    private String skuCode;

    @Schema(description = "规格组合：{\"颜色\":\"星空灰\",\"尺码\":\"XL\"}。无规格商品填 {}")
    private String skuAttrs;

    @Schema(description = "该规格专属图 file_id：C端切换规格时换主图，为空则用商品封面")
    private Long skuCoverFileId;

    @Schema(description = "本规格所需积分：为空则继承 t_mall_commodity.points_price")
    private Integer skuPointsPrice;

    @Schema(description = "本规格所需现金：为空则继承 t_mall_commodity.cash_price")
    private BigDecimal skuCashPrice;

    @Schema(description = "总库存：运营投放量，恒定不变，补货改这里")
    private Integer totalStock;

    @Schema(description = "锁定库存：已下单未履约（仅 pay_type=2 会悬挂）")
    private Integer lockedStock;

    @Schema(description = "已售数量：履约成功累加")
    private Integer soldCount;

    @Schema(description = "可用库存（虚拟列，勿写入）")
    private Integer availableStock;

    @Schema(description = "状态：0-停用, 1-启用")
    private Integer skuStatus;

    @Schema(description = "排序")
    private Integer sort;

    @Schema(description = "创建人")
    private String createBy;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    @Schema(description = "更新人")
    private String updateBy;

    @Schema(description = "更新时间")
    private LocalDateTime updateTime;

}
