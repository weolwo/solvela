package sa.mall.commodity.domain.form;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import sa.base.common.util.SmartCodeUtil;

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
 *   <li>SKU 的 {@code lockedStock / soldCount / availableStock}，见 {@link MallCommoditySkuForm}</li>
 * </ul>
 *
 * @Date 2026-08-23
 */
@Data
public class MallCommoditySaveForm {

    @Schema(description = "商品id：新建传 null")
    private Long id;

    /**
     * 商品编码。<b>运营可以手输，也可以点「生成」</b> —— SmartCodeUtil 的注释写明了这两条路径
     * 都要由服务端校验格式并判重，所以这里收前端的值，而不是服务端自说自话地生成。
     *
     * <p>创建后不可改（DDL 注释写死）：编辑时传什么都会被忽略，以库里那份为准。
     * 前端也把编辑态的输入框禁掉了，但那只是防呆，真正的约束在服务端。
     */
    @Schema(description = "商品编码：10位大写字母+数字，全局唯一，创建后不可改")
    @Pattern(regexp = SmartCodeUtil.BIZ_CODE_REGEX, message = "商品" + SmartCodeUtil.BIZ_CODE_MESSAGE)
    private String commodityCode;

    @Schema(description = "分类id", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "请选择商品分类")
    private Long categoryId;

    /**
     * 取值对齐 {@code sa.enums.PrizeTypeEnum}（不含 SCORE，积分换积分无意义）——
     * 履约 handler 就是按它分派的，类型对齐了发货/发券基本不用写新代码。
     */
    @Schema(description = "商品类型：PHYSICAL-实物, COUPON-优惠券, BALANCE-现金/红包", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "请选择商品类型")
    private String commodityType;

    @Schema(description = "资产引用：COUPON 存券模编码，PHYSICAL 留空")
    private String assetRef;

    @Schema(description = "商品名称", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "商品名称不能为空")
    @Size(max = 128, message = "商品名称最长 128 字")
    private String commodityName;

    @Schema(description = "副标题/一句话卖点")
    @Size(max = 255, message = "副标题最长 255 字")
    private String commodityIntro;

    @Schema(description = "封面主图 file_id", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "请上传商品封面")
    private Long coverFileId;

    /**
     * 轮播图，顺序即展示顺序。不落商品表的列，而是登记进 {@code t_file_relation} 的
     * {@code MALL_COMMODITY_BANNER} 组 —— 那张表的 sort 列注释原文就是「轮播图必需」。
     */
    @Schema(description = "轮播图 file_id 列表，顺序即展示顺序")
    private List<Long> bannerFileIds = new ArrayList<>();

    @Schema(description = "图文详情，富文本HTML。禁止 base64 内联图片")
    private String detailContent;

    @Schema(description = "兑换须知：券的核销说明、实物的发货时效等")
    @Size(max = 1024, message = "兑换须知最长 1024 字")
    private String exchangeNotice;

    @Schema(description = "支付方式：1-纯积分, 2-积分+现金", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "请选择支付方式")
    private Integer payType;

    @Schema(description = "划线原价：仅前端展示「价值￥199」，纯积分商品可留 0")
    @DecimalMin(value = "0", message = "划线原价不能为负")
    private BigDecimal originalPrice;

    @Schema(description = "基准兑换积分", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "请填写基准兑换积分")
    @Min(value = 0, message = "基准兑换积分不能为负")
    private Integer pointsPrice;

    @Schema(description = "基准兑换现金：pay_type=1 时服务端强制归零")
    @DecimalMin(value = "0", message = "基准兑换现金不能为负")
    private BigDecimal cashPrice;

    @Schema(description = "限兑周期：LIFETIME / DAILY / WEEKLY / MONTHLY")
    private String limitPeriod;

    @Schema(description = "周期内单会员限兑件数：0-不限制")
    @Min(value = 0, message = "限兑件数不能为负")
    private Integer limitCount;

    /**
     * 商品的<b>上架有效期</b>，不是秒杀场次 —— DDL 把这个语义钉死过，别拿来当档期用。
     * 留空由服务端填哨兵值（列是 NOT NULL 的，理由见 {@code MallConst.SHELF_START_SENTINEL}）。
     */
    @Schema(description = "上架开始时间：留空表示不限")
    private LocalDateTime startTime;

    @Schema(description = "上架结束时间：留空表示不限")
    private LocalDateTime endTime;

    /**
     * 编辑页顶部两个按钮的差别只在这个值上：「存草稿」传 2，「保存并上架」传 1。
     * 上架会触发一组额外校验（至少一个启用 SKU 等），草稿不校验 —— 运营常常要先存一半。
     */
    @Schema(description = "状态：0-下架, 1-上架, 2-草稿")
    private Integer status;

    @Schema(description = "是否首页推荐：0-否, 1-是")
    private Integer isHome;

    @Schema(description = "排序权重：从小到大")
    private Integer sort;

    /**
     * SKU 列表。<b>整表提交，不是增量</b>：库里有、这次没传的行会被删掉
     * （有锁定/已售的行会被拒绝删除并提示改为停用）。
     *
     * <p>传空列表是合法的：无规格商品由服务端自动补一条 {@code {}} 的默认 SKU，
     * 而不是让运营手工去建一条「没有规格的规格」。
     */
    @Schema(description = "SKU 列表：整表提交")
    @Valid
    private List<MallCommoditySkuForm> skuList = new ArrayList<>();
}
