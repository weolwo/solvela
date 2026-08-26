package solvela.draw.drawlog.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.util.List;
import lombok.Data;

/**
 * 抽奖转化漏斗：把一堆流水行压成「这个奖池到底跑得怎么样」。
 *
 * <p>原先的记录页是 11 列裸字段、零聚合，翻十页也答不出最基本的问题：
 * 中奖率多少、多少人抽到的是「手慢了」、哪个奖发得最多。
 * 而这些数字流水里全都有 —— 只是从来没人算过。
 *
 * @Author alaric
 * @Date 2026-08-16
 */
@Data
public class DrawFunnelDTO {

    @Schema(description = "抽奖总次数（筛选范围内）")
    private Long totalCount;

    @Schema(description = "中奖次数：status=1")
    private Long hitCount;

    @Schema(description = "未中奖次数：status=0")
    private Long missCount;

    /**
     * 库存不足次数：status=2。用户看到的是「手慢了，奖品已被抽完」。
     * 这个数偏高说明奖池缺货或兜底失效，是运营最该盯的信号 —— 它不是「没中奖」，
     * 而是「系统没东西可给」，两者对用户体验的含义完全不同。
     */
    @Schema(description = "库存不足次数：status=2，用户看到「手慢了，奖品已被抽完」")
    private Long noStockCount;

    @Schema(description = "异常次数：status=3")
    private Long errorCount;

    @Schema(description = "中奖率 = 中奖 / 总次数")
    private BigDecimal hitRate;

    @Schema(description = "库存不足率 = 库存不足 / 总次数，衡量奖池缺货严重程度")
    private BigDecimal noStockRate;

    @Schema(description = "参与人数（去重会员数）")
    private Long memberCount;

    @Schema(description = "人均抽奖次数")
    private BigDecimal drawPerMember;

    @Schema(description = "奖品发放分布，按次数降序")
    private List<PrizeHitVO> prizeHitList;

    /**
     * 一个奖品的发放情况
     */
    @Data
    public static class PrizeHitVO {

        @Schema(description = "奖品编码")
        private String prizeCode;

        @Schema(description = "奖品名称，奖品已删除时为 null")
        private String prizeName;

        @Schema(description = "奖品类型")
        private String prizeType;

        @Schema(description = "发放次数")
        private Long hitCount;

        @Schema(description = "占全部中奖的比例")
        private BigDecimal hitShare;

        @Schema(description = "已发放价值 = 次数 × 单价")
        private BigDecimal issuedValue;
    }
}
