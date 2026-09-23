package solvela.admin.module.mall.gradeprice.domain.form;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 单品覆盖价 新增/编辑表单。
 *
 * <p>⚠️ 这里只做「格式对不对」。<b>四条业务校验在 {@code MallGradePriceService}</b> ——
 * 「不能高于挂牌价」要读商品、「商品得开着等级折扣开关」要读商品、
 * 「规格得属于这件商品」要读 SKU，而表单校验只看得见自己这一行。
 *
 * @author alaric
 * @date 2026-09-23
 */
@Data
public class MallGradePriceForm {

    @Schema(description = "id。新增时不传")
    private Long id;

    @Schema(description = "商品 id", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "请选择商品")
    private Long commodityId;

    /**
     * 规格 id；不传或 0 = 整个商品。
     *
     * <p>🔴 <b>用 0 不用 null。</b>唯一键是 {@code (commodity_id, sku_id, grade_code)}，
     * 而 MySQL 的唯一索引不约束 NULL —— 落成 null 的话「整个商品对钻石的价」能有两条，
     * 查出来是哪条取决于存储顺序。
     */
    @Schema(description = "规格 id；0 或不传 = 整个商品")
    @Min(value = 0, message = "规格 id 不能为负")
    private Long skuId;

    @Schema(description = "等级值，必须大于 0", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "请选择等级")
    @Min(value = 1, message = "最低档（等级 0）不能配覆盖价")
    private Integer gradeCode;

    /**
     * 这一档就这个价。
     *
     * <p>⚠️ {@code 0} 是合法的 —— 这一档免费兑换。所以下限是 0 不是 1，
     * 与 {@code sku_points_price} 那条「0 是真免费，不是未设置」同源。
     */
    @Schema(description = "覆盖价（积分）。0 = 这一档免费", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "请填写覆盖价")
    @Min(value = 0, message = "覆盖价不能为负")
    private Integer pointsPrice;
}
