package solvela.mall.commodity.domain.command;

import solvela.enums.MallPayTypeEnum;
import solvela.enums.MallCommodityStatusEnum;
import lombok.Data;
import solvela.base.util.SolvelaCodeUtil;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 商品聚合保存表单：主表 + SKU + 轮播图，一次提交、一个事务。
 *
 * <p><b>为什么不是 add/update 两个接口</b>：商品编辑页是一个整体，运营点一次「保存」。
 * 拆成 N 个请求的话，「主表存进去了、SKU 第 3 条失败」会留下一个半截商品，
 * 而前端根本没有能力回滚已经成功的那几个请求。
 *
 * <p><b>刻意不收的字段</b>（收了就等于允许表单改运行态数据）：
 * <ul>
 *   <li>{@code soldCount} —— 累计已兑，履约链路维护</li>
 *   <li>SKU 的 {@code lockedStock / soldCount / availableStock}，见 {@link MallCommoditySkuCommand}</li>
 * </ul>
 *
 * @Date 2026-08-23
 */
@Data
public class MallCommoditySaveCommand {

    /** 商品id：新建传 null */
    private Long id;

    /**
     * 商品编码。<b>运营可以手输，也可以点「生成」</b> —— SolvelaCodeUtil 的注释写明了这两条路径
     * 都要由服务端校验格式并判重，所以这里收前端的值，而不是服务端自说自话地生成。
     *
     * <p>创建后不可改（DDL 注释写死）：编辑时传什么都会被忽略，以库里那份为准。
     * 前端也把编辑态的输入框禁掉了，但那只是防呆，真正的约束在服务端。
     */
    private String commodityCode;

    /** 分类id */
    private Long categoryId;

    /**
     * 取值对齐 {@code solvela.enums.PrizeTypeEnum}（不含 SCORE，积分换积分无意义）——
     * 履约 handler 就是按它分派的，类型对齐了发货/发券基本不用写新代码。
     */
    private String commodityType;

    /** 资产引用：COUPON 存券模编码，PHYSICAL 留空 */
    private String assetRef;

    /** 商品名称 */
    private String commodityName;

    /** 副标题/一句话卖点 */
    private String commodityIntro;

    /** 封面主图 file_id */
    private Long coverFileId;

    /**
     * 轮播图，顺序即展示顺序。不落商品表的列，而是登记进 {@code t_file_relation} 的
     * {@code MALL_COMMODITY_BANNER} 组 —— 那张表的 sort 列注释原文就是「轮播图必需」。
     */
    private List<Long> bannerFileIds = new ArrayList<>();

    /** 图文详情，富文本HTML。禁止 base64 内联图片 */
    private String detailContent;

    /** 兑换须知：券的核销说明、实物的发货时效等 */
    private String exchangeNotice;

    /** 支付方式：1-纯积分, 2-积分+现金 */
    private MallPayTypeEnum payType;

    /** 划线原价：仅前端展示「价值￥199」，纯积分商品可留 0 */
    private BigDecimal originalPrice;

    /** 基准兑换积分 */
    private Integer pointsPrice;

    /** 基准兑换现金：pay_type=1 时服务端强制归零 */
    private BigDecimal cashPrice;

    /** 限兑周期：LIFETIME / DAILY / WEEKLY / MONTHLY */
    private String limitPeriod;

    /** 周期内单会员限兑件数：0-不限制 */
    private Integer limitCount;

    /**
     * 商品的<b>上架有效期</b>，不是秒杀场次 —— DDL 把这个语义钉死过，别拿来当档期用。
     * 留空由服务端填哨兵值（列是 NOT NULL 的，理由见 {@code MallConst.SHELF_START_SENTINEL}）。
     */
    private LocalDateTime startTime;

    /** 上架结束时间：留空表示不限 */
    private LocalDateTime endTime;

    /**
     * 编辑页顶部两个按钮的差别只在这个值上：「存草稿」传 2，「保存并上架」传 1。
     * 上架会触发一组额外校验（至少一个启用 SKU 等），草稿不校验 —— 运营常常要先存一半。
     */
    private MallCommodityStatusEnum status;

    /** 是否首页推荐：0-否, 1-是 */
    private Boolean isHome;

    /** 排序权重：从小到大 */
    private Integer sort;

    /**
     * SKU 列表。<b>整表提交，不是增量</b>：库里有、这次没传的行会被删掉
     * （有锁定/已售的行会被拒绝删除并提示改为停用）。
     *
     * <p>传空列表是合法的：无规格商品由服务端自动补一条 {@code {}} 的默认 SKU，
     * 而不是让运营手工去建一条「没有规格的规格」。
     */
    private List<MallCommoditySkuCommand> skuList = new ArrayList<>();
}
