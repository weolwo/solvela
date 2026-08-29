package solvela.mall.commodity.domain.dto;

import solvela.enums.MallPayTypeEnum;
import solvela.enums.MallCommodityStatusEnum;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 商城商品列表的<b>读模型</b>：mapper 的 resultMap 映射到这里，service 也返回它。
 *
 * <p>DTO 是领域能查出来的全部，VO 是某个端决定给出去的那一部分，装配在端上做。
 * C 端将来接这条玩法时写自己的 VO，不必迁就管理端的字段。完整说明见 {@code MemberWalletDTO}。
 */

@Data
public class MallCommodityDTO {


    private Long id;

    /** 商品编码：10位大写字母+数字，全局唯一，创建后不可改 */
    private String commodityCode;

    /** 分类id */
    private Long categoryId;

    /** join 出来的展示字段。列表里显示「分类id 7」没有人看得懂 */
    private String categoryName;

    /** 商品类型：PHYSICAL-实物(走t_physical_delivery), COUPON-优惠券(走t_member_coupon), BALANCE-现金/红包(走钱包入账) */
    private String commodityType;

    /** 资产引用：COUPON 存券模编码，PHYSICAL 为空。语义对齐 t_proposal_record.asset_ref */
    private String assetRef;

    /** 商品名称 */
    private String commodityName;

    /** 副标题/一句话卖点 */
    private String commodityIntro;

    /** 封面主图 file_id（建议 800x800） */
    private Long coverFileId;

    /** 兑换须知：券的核销说明、实物的发货时效等。C端下单页固定展示 */
    private String exchangeNotice;

    /** 支付方式：1-纯积分, 2-积分+现金 */
    private MallPayTypeEnum payType;

    /** 划线原价：仅前端展示「价值￥199」，纯积分商品可留 0 */
    private BigDecimal originalPrice;

    /** 基准兑换积分 */
    private Integer pointsPrice;

    /** 基准兑换现金：pay_type=1 时恒为 0 */
    private BigDecimal cashPrice;

    /** 限兑周期：LIFETIME-终身, DAILY-每日, WEEKLY-每周, MONTHLY-每月 */
    private String limitPeriod;

    /** 周期内单会员限兑件数：0-不限制 */
    private Integer limitCount;

    /** 上架开始时间：默认值代表不限。不是秒杀场次 */
    private LocalDateTime startTime;

    /** 上架结束时间：默认值代表不限。不是秒杀场次 */
    private LocalDateTime endTime;

    /** 状态：0-下架, 1-上架, 2-草稿。新建默认落草稿 */
    private MallCommodityStatusEnum status;

    /** 是否首页推荐：0-否, 1-是 */
    private Integer isHome;

    /** 排序权重：从小到大 */
    private Integer sort;

    /** 累计已兑件数（各SKU之和的冗余，用于列表按热销排序） */
    private Integer soldCount;

    // ---------- 以下由 SKU 表聚合而来，只用于列表展示 ----------
    //
    // 放在 SQL 里聚合而不是查出商品再逐个查 SKU：一页 10 个商品就是 10 次额外查询，
    // 而运营翻页是很频繁的动作。

    /** 规格数量 */
    private Integer skuCount;

    /** 总库存：各SKU投放量之和 */
    private Integer totalStock;

    /**
     * 可用库存合计。运营在列表上最需要的一眼信息就是这个 ——
     * 「投了 100 件，现在还剩几件能兑」，不用点进去逐个规格看。
     */
    private Integer availableStock;

    /** 创建人 */
    private String createBy;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新人 */
    private String updateBy;

    /** 更新时间 */
    private LocalDateTime updateTime;

}
