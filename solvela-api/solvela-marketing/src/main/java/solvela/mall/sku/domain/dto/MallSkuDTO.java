package solvela.mall.sku.domain.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 商城SKU列表的<b>读模型</b>：mapper 的 resultMap 映射到这里，service 也返回它。
 *
 * <p>DTO 是领域能查出来的全部，VO 是某个端决定给出去的那一部分，装配在端上做。
 * C 端将来接这条玩法时写自己的 VO，不必迁就管理端的字段。完整说明见 {@code MemberWalletDTO}。
 */

@Data
public class MallSkuDTO {


    private Long id;

    /** 关联 t_mall_commodity.id */
    private Long commodityId;

    // ---------- join 出来的商品信息：只看 SKU 编码认不出这是哪个商品的哪个规格 ----------

    /** 商品名称 */
    private String commodityName;

    /** 商品编码 */
    private String commodityCode;

    /** 商品封面 file_id */
    private Long coverFileId;

    /** 商品状态：0-下架, 1-上架, 2-草稿 */
    private Integer commodityStatus;

    /** 商品基准积分（SKU 未覆盖时的实际售价） */
    private Integer basePointsPrice;

    /** 分类id */
    private Long categoryId;

    /** 分类名称 */
    private String categoryName;

    /** SKU编码：10位大写字母+数字，全局唯一 */
    private String skuCode;

    /** 规格组合：{\ */
    private String skuAttrs;

    /** 该规格专属图 file_id：C端切换规格时换主图，为空则用商品封面 */
    private Long skuCoverFileId;

    /** 本规格所需积分：为空则继承 t_mall_commodity.points_price */
    private Integer skuPointsPrice;

    /** 本规格所需现金：为空则继承 t_mall_commodity.cash_price */
    private BigDecimal skuCashPrice;

    /** 总库存：运营投放量，恒定不变，补货改这里 */
    private Integer totalStock;

    /** 锁定库存：已下单未履约（仅 pay_type=2 会悬挂） */
    private Integer lockedStock;

    /** 已售数量：履约成功累加 */
    private Integer soldCount;

    /** 可用库存（虚拟列，勿写入） */
    private Integer availableStock;

    /** 状态：0-停用, 1-启用 */
    private Integer skuStatus;

    /** 排序 */
    private Integer sort;

    /** 创建人 */
    private String createBy;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新人 */
    private String updateBy;

    /** 更新时间 */
    private LocalDateTime updateTime;

}
