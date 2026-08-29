package solvela.mall.commodity.domain.dto;

import solvela.enums.MallPayTypeEnum;
import solvela.enums.MallCommodityStatusEnum;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 商品编辑页的聚合回显：主表 + SKU + 轮播图。
 *
 * <p><b>与 {@code MallCommoditySaveCommand} 刻意保持同构</b>：前端拿到即可直接填进表单，
 * 保存时原样回传。两边字段一旦分叉，就会出现「回显对、保存后少了一个字段」这种
 * 只有特定操作路径才复现的 bug。
 *
 * @Date 2026-08-23
 */
@Data
public class MallCommodityDetailDTO {

    /** 商品id */
    private Long id;

    /** 商品编码：服务端生成，创建后不可改 */
    private String commodityCode;

    /** 分类id */
    private Long categoryId;

    /** 回显用。分类可能已被停用/改名，带上名字前端就不用再查一次分类表 */
    private String categoryName;

    /** 商品类型：PHYSICAL / COUPON / BALANCE */
    private String commodityType;

    /** 资产引用：COUPON 存券模编码 */
    private String assetRef;

    /** 商品名称 */
    private String commodityName;

    /** 副标题/一句话卖点 */
    private String commodityIntro;

    /** 封面主图 file_id */
    private Long coverFileId;

    /** 轮播图 file_id 列表，按 sort 排 */
    private List<Long> bannerFileIds = new ArrayList<>();

    /** 图文详情，富文本HTML */
    private String detailContent;

    /** 兑换须知 */
    private String exchangeNotice;

    /** 支付方式：1-纯积分, 2-积分+现金 */
    private MallPayTypeEnum payType;

    /** 划线原价 */
    private BigDecimal originalPrice;

    /** 基准兑换积分 */
    private Integer pointsPrice;

    /** 基准兑换现金 */
    private BigDecimal cashPrice;

    /** 限兑周期 */
    private String limitPeriod;

    /** 周期内单会员限兑件数：0-不限制 */
    private Integer limitCount;

    /**
     * 哨兵值（1970 / 2099）表示「不限」。<b>原样下发不做转换</b> ——
     * 转成 null 的话，前端保存时再传回 null，服务端又填一次哨兵，来回翻译容易出错；
     * 由前端按哨兵值渲染成空的时间选择器即可。
     */
    private LocalDateTime startTime;

    /** 上架结束时间：2099 哨兵值表示不限 */
    private LocalDateTime endTime;

    /** 状态：0-下架, 1-上架, 2-草稿 */
    private MallCommodityStatusEnum status;

    /** 是否首页推荐：0-否, 1-是 */
    private Integer isHome;

    /** 排序权重 */
    private Integer sort;

    /** 累计已兑件数（只读） */
    private Integer soldCount;

    /** SKU 列表 */
    private List<MallCommoditySkuDTO> skuList = new ArrayList<>();

    /** 创建人 */
    private String createBy;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新人 */
    private String updateBy;

    /** 更新时间 */
    private LocalDateTime updateTime;
}
