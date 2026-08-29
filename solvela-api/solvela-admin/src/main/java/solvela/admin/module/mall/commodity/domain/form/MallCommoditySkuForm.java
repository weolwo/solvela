package solvela.admin.module.mall.commodity.domain.form;

import solvela.enums.EnableStatusEnum;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;
import solvela.base.util.SolvelaCodeUtil;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 商品保存表单里的一行 SKU。
 *
 * <p><b>也没有 lockedStock / soldCount / availableStock</b>：这三个是运行态数据，
 * 只能由下单、履约、超时释放那几条链路改。放进表单等于允许运营用一次保存把已售清零。
 *
 * @Date 2026-08-23
 */
@Data
public class MallCommoditySkuForm {

    @Schema(description = "SKU id：新增行传 null")
    private Long id;

    /**
     * SKU 编码，规则同商品编码：运营可改、服务端判重、创建后不可改。
     * 履约链路按编码引用 SKU（{@code t_proposal_record.asset_ref} 的注释原文就是「券模/SKU」），
     * 改一次等于把已有的引用指向空。
     */
    @Schema(description = "SKU编码：10位大写字母+数字，全局唯一，创建后不可改")
    @Pattern(regexp = SolvelaCodeUtil.BIZ_CODE_REGEX, message = "SKU" + SolvelaCodeUtil.BIZ_CODE_MESSAGE)
    private String skuCode;

    /**
     * 规格组合。<b>无规格商品也必须有一行</b>，这里传空 map，落库成 {@code {}} ——
     * DDL 注释写死了这条：没有「没有 SKU 的商品」，否则下单链路要为它写一套分支。
     *
     * <p>用 LinkedHashMap 保序：运营录的是「颜色 / 尺码」，回显时顺序变了会让人以为改错了东西。
     */
    @Schema(description = "规格组合：{\"颜色\":\"星空灰\",\"尺码\":\"XL\"}。无规格填 {}")
    private Map<String, String> skuAttrs = new LinkedHashMap<>();

    @Schema(description = "该规格专属图 file_id：为空则用商品封面")
    private Long skuCoverFileId;

    /**
     * 为空表示<b>继承主表基准价</b>，不是 0。
     * 0 是「免费兑换」的合法取值，用 0 当"未设置"就分不清「没填」和「真免费」了 —— DDL 里专门解释过。
     */
    @Schema(description = "本规格所需积分：为空则继承商品基准积分")
    @Min(value = 0, message = "SKU 积分不能为负")
    private Integer skuPointsPrice;

    @Schema(description = "本规格所需现金：为空则继承商品基准现金")
    @DecimalMin(value = "0", message = "SKU 现金价不能为负")
    private BigDecimal skuCashPrice;

    @Schema(description = "总库存：运营投放量，补货改这里", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "SKU 总库存不能为空")
    @Min(value = 0, message = "SKU 总库存不能为负")
    private Integer totalStock;

    @Schema(description = "状态：0-停用, 1-启用")
    private EnableStatusEnum skuStatus;

    @Schema(description = "排序：从小到大")
    private Integer sort;
}
