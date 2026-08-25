package solvela.draw.prizemapping.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.util.List;
import lombok.Data;

/**
 * 奖池里的一个坑位：概率区间 + 奖品 + 库存 + 体检。
 *
 * <p>表里只有 {@code probability} 一个数，但引擎真正用来判定的是<b>累加出来的区间</b>
 * （{@code ProbabilityRange}，按 sortWeight 顺序累加）。
 * 随机数落在 [min, max) 才命中这个坑位 —— 区间从来没在页面上露过面，
 * 而它才是「为什么这个奖抽不到」的答案。
 *
 * @Author alaric
 * @Date 2026-08-16
 */
@Data
public class DrawPoolSlotVO {

    @Schema(description = "坑位映射id")
    private Long id;

    @Schema(description = "坑位顺序，引擎按它累加概率区间")
    private Integer sortWeight;

    @Schema(description = "奖项id")
    private Long prizeItemId;

    @Schema(description = "奖品编码")
    private String prizeCode;

    @Schema(description = "奖品名称，奖品缺失时为 null")
    private String prizeName;

    @Schema(description = "奖品类型")
    private String prizeType;

    @Schema(description = "奖品单价")
    private BigDecimal prizeValue;

    @Schema(description = "配置的中奖概率（百分比）")
    private BigDecimal probability;

    /**
     * 引擎实际判定用的区间下界（含）。随机数 ∈ [rangeMin, rangeMax) 命中本坑位。
     * 之所以要显示它：概率一样的两个坑位，区间位置不同，排查「谁抢了谁」时只能看区间。
     */
    @Schema(description = "命中区间下界（含）")
    private BigDecimal rangeMin;

    @Schema(description = "命中区间上界（不含）")
    private BigDecimal rangeMax;

    @Schema(description = "是否兜底奖项：概率命中的奖项无库存时降级到它")
    private Boolean fallback;

    @Schema(description = "总库存，-1 表示不限量")
    private Integer totalStock;

    @Schema(description = "已出数量（DB 口径，跨奖池累计）")
    private Integer usedStock;

    @Schema(description = "DB 口径剩余库存，不限量时为 null")
    private Integer remainStockDb;

    /**
     * Redis 口径剩余库存 —— 运行态真正用来预扣的那个数。
     * 与 DB 口径不一致就是事故信号（缓存被误预热、回滚失败），必须让人看得到。
     */
    @Schema(description = "Redis 口径剩余库存（运行态实际预扣依据），未预热时为 null")
    private Integer remainStockCache;

    /**
     * 单次抽奖在这个坑位上的期望赔付 = 概率 × 奖品单价。
     * 逐坑位加起来就是「抽一次平均要赔多少钱」，配概率时最该先看的数。
     */
    @Schema(description = "单次抽奖的期望赔付 = 概率 × 单价")
    private BigDecimal expectedCostPerDraw;

    @Schema(description = "本坑位的体检告警")
    private List<DrawPoolIssueVO> issueList;
}
