package solvela.draw.poolitem.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.util.List;
import lombok.Data;

/**
 * 一个奖项的库存看板行。
 *
 * <p>原先这张表的列表页把 {@code total_stock} / {@code used_stock} 两个裸数字并排一放就完事，
 * 而运营真正要问的是三件事：<b>还剩多少、消耗得多快、会不会提前抽空</b>。
 * 更要紧的是——运行态预扣用的根本不是 {@code used_stock}，而是 Redis 里的剩余量，
 * 只看 DB 口径是个安慰性的数字。
 *
 * @Author alaric
 * @Date 2026-08-16
 */
@Data
public class PrizeItemStockVO {

    @Schema(description = "奖项id")
    private Long id;

    @Schema(description = "归属活动编码")
    private String activityCode;

    @Schema(description = "活动状态: 0-未上线, 1-已上线")
    private Integer activityStatus;

    @Schema(description = "奖品编码")
    private String prizeCode;

    @Schema(description = "奖品名称，奖品缺失时为 null")
    private String prizeName;

    @Schema(description = "奖品类型")
    private String prizeType;

    @Schema(description = "奖品单价")
    private BigDecimal prizeValue;

    @Schema(description = "总库存，-1 表示不限量")
    private Integer totalStock;

    @Schema(description = "已出数量（DB 口径，跨奖池累计）")
    private Integer usedStock;

    @Schema(description = "DB 口径剩余，不限量时为 null")
    private Integer remainStockDb;

    /**
     * Redis 口径剩余 —— 运行态 Lua 预扣真正依据的那个数。
     * 与 DB 口径不一致就是事故信号：缓存被误预热、或扣减后回滚失败。
     */
    @Schema(description = "Redis 口径剩余（运行态预扣依据），未预热时为 null")
    private Integer remainStockCache;

    @Schema(description = "两个口径的差值（Redis - DB），0 表示一致")
    private Integer stockDrift;

    @Schema(description = "消耗率 = 已出 / 总库存，不限量时为 null")
    private BigDecimal usedRate;

    @Schema(description = "已发出奖品的累计价值 = 已出数量 × 单价")
    private BigDecimal issuedValue;

    @Schema(description = "剩余库存的敞口价值 = DB剩余 × 单价，即最坏还要赔多少")
    private BigDecimal remainValue;

    @Schema(description = "单人限领次数: -1 不限")
    private Integer userMaxCount;

    @Schema(description = "白名单人数（必中名单），解析失败时为 null")
    private Integer whiteListCount;

    /**
     * 被哪些奖池的坑位引用。一个奖项可以出现在多个奖池里，
     * 库存是<b>跨奖池共享</b>的 —— 这一点在原页面上完全看不出来，
     * 而它正是「为什么另一个池的奖也被抽空了」的答案。
     */
    @Schema(description = "引用本奖项的奖池编码")
    private List<String> poolCodeList;

    @Schema(description = "本奖项的体检告警")
    private List<PrizeItemIssueVO> issueList;
}
