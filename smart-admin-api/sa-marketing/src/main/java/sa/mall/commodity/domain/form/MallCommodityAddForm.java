package sa.mall.commodity.domain.form;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 商城-商品主表 新建表单
 *
 * @Author weolwo
 * @Date 2026-08-22 19:29:59
 * @Copyright weolwo
 */

@Data
public class MallCommodityAddForm {

    @Schema(description = "id", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "id 不能为空")
    private Long id;

    @Schema(description = "商品编码：10位大写字母+数字，全局唯一，创建后不可改", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "商品编码：10位大写字母+数字，全局唯一，创建后不可改 不能为空")
    private String commodityCode;

    @Schema(description = "分类id", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "分类id 不能为空")
    private Long categoryId;

    @Schema(description = "商品类型：PHYSICAL-实物(走t_physical_delivery), COUPON-优惠券(走t_member_coupon), BALANCE-现金/红包(走钱包入账)", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "商品类型：PHYSICAL-实物(走t_physical_delivery), COUPON-优惠券(走t_member_coupon), BALANCE-现金/红包(走钱包入账) 不能为空")
    private String commodityType;

    @Schema(description = "商品名称", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "商品名称 不能为空")
    private String commodityName;

    @Schema(description = "封面主图 file_id（建议 800x800）", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "封面主图 file_id（建议 800x800） 不能为空")
    private Long coverFileId;

    @Schema(description = "支付方式：1-纯积分, 2-积分+现金", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "支付方式：1-纯积分, 2-积分+现金 不能为空")
    private Integer payType;

    @Schema(description = "划线原价：仅前端展示「价值￥199」，纯积分商品可留 0", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "划线原价：仅前端展示「价值￥199」，纯积分商品可留 0 不能为空")
    private BigDecimal originalPrice;

    @Schema(description = "基准兑换积分", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "基准兑换积分 不能为空")
    private Integer pointsPrice;

    @Schema(description = "基准兑换现金：pay_type=1 时恒为 0", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "基准兑换现金：pay_type=1 时恒为 0 不能为空")
    private BigDecimal cashPrice;

    @Schema(description = "限兑周期：LIFETIME-终身, DAILY-每日, WEEKLY-每周, MONTHLY-每月", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "限兑周期：LIFETIME-终身, DAILY-每日, WEEKLY-每周, MONTHLY-每月 不能为空")
    private String limitPeriod;

    @Schema(description = "周期内单会员限兑件数：0-不限制", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "周期内单会员限兑件数：0-不限制 不能为空")
    private Integer limitCount;

    @Schema(description = "上架开始时间：默认值代表不限。不是秒杀场次", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "上架开始时间：默认值代表不限。不是秒杀场次 不能为空")
    private LocalDateTime startTime;

    @Schema(description = "上架结束时间：默认值代表不限。不是秒杀场次", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "上架结束时间：默认值代表不限。不是秒杀场次 不能为空")
    private LocalDateTime endTime;

    @Schema(description = "状态：0-下架, 1-上架, 2-草稿。新建默认落草稿", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "状态：0-下架, 1-上架, 2-草稿。新建默认落草稿 不能为空")
    private Integer status;

    @Schema(description = "是否首页推荐：0-否, 1-是", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "是否首页推荐：0-否, 1-是 不能为空")
    private Integer isHome;

    @Schema(description = "排序权重：从小到大", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "排序权重：从小到大 不能为空")
    private Integer sort;

    @Schema(description = "累计已兑件数（各SKU之和的冗余，用于列表按热销排序）", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "累计已兑件数（各SKU之和的冗余，用于列表按热销排序） 不能为空")
    private Integer soldCount;

}