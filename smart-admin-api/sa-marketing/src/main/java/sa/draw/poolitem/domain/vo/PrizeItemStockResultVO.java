package sa.draw.poolitem.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.util.List;
import lombok.Data;

/**
 * 库存看板的整体返回：顶部概览 + 当前页的奖项明细。
 *
 * <p>概览与明细在同一次请求里算出来，卡片数字与列表必然一致。
 *
 * @Author alaric
 * @Date 2026-08-16
 */
@Data
public class PrizeItemStockResultVO {

    @Schema(description = "奖项总数（筛选后）")
    private Integer itemCount;

    @Schema(description = "已售罄的奖项数：命中会降级到兜底，无兜底则返回「已被抽完」")
    private Integer soldOutCount;

    /**
     * 两个库存口径不一致的奖项数。这是最该被盯住的一个数字：
     * 运行态按 Redis 预扣，DB 只是兜底，漂移意味着可能超发或少发。
     */
    @Schema(description = "库存口径漂移的奖项数：Redis 与 DB 剩余对不上")
    private Integer driftCount;

    @Schema(description = "DANGER 级告警总数")
    private Integer dangerCount;

    @Schema(description = "WARN 级告警总数")
    private Integer warnCount;

    @Schema(description = "已发出奖品累计价值")
    private BigDecimal totalIssuedValue;

    @Schema(description = "剩余库存敞口价值：全部抽完最坏还要赔多少")
    private BigDecimal totalRemainValue;

    @Schema(description = "当前页的奖项明细")
    private List<PrizeItemStockVO> list;

    @Schema(description = "奖项总数，供前端分页")
    private Long total;
}
