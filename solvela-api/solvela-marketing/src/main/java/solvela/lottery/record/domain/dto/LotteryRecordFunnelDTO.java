package solvela.lottery.record.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.util.List;
import lombok.Data;

/**
 * 购彩记录漏斗：把一堆号码行压成「这一期跑得怎么样、奖发出去没有」。
 *
 * <p>原先的记录页是 15 列裸字段、零聚合，翻十页也答不出最基本的问题：
 * 中奖率多少、各奖级各中了几注、中了奖的到底发出去没有。而这些流水里全都有。
 *
 * <h3>派发漏斗是本页独有的价值</h3>
 * 中奖只是第一步，奖品要经派发链路真正到用户手上才算完。
 * {@code dispatch_status} 记录了那一段：0-待派发/无需派发、1-已投递、2-投递失败。
 * <b>「已中奖但投递失败」是全模块最该被盯住的数字</b> ——
 * 用户看到自己中了奖、系统也认，但东西没发出去，而这种事没人主动查就不会被发现。
 *
 * @Author alaric
 * @Date 2026-08-16
 */
@Data
public class LotteryRecordFunnelDTO {

    @Schema(description = "号码总数（筛选范围内）")
    private Long totalCount;

    @Schema(description = "未开奖：win_status=0")
    private Long waitCount;

    @Schema(description = "未中奖：win_status=1")
    private Long loseCount;

    @Schema(description = "已中奖：win_status=2")
    private Long winCount;

    @Schema(description = "中奖率 = 已中奖 / 已开奖（未开奖的不计入分母）")
    private BigDecimal winRate;

    @Schema(description = "参与人数（去重会员数）")
    private Long memberCount;

    @Schema(description = "人均领号数")
    private BigDecimal ticketPerMember;

    // ---------------- 派发漏斗 ----------------

    @Schema(description = "中奖且待派发：dispatch_status=0")
    private Long dispatchWaitCount;

    @Schema(description = "中奖且已投递：dispatch_status=1")
    private Long dispatchedCount;

    /**
     * 中奖但投递失败。用户中了奖却没拿到东西 —— 全模块最该被盯住的数字。
     */
    @Schema(description = "中奖但投递失败：dispatch_status=2")
    private Long dispatchFailedCount;

    @Schema(description = "奖级分布，按奖级升序（数字越小奖越大）")
    private List<PrizeLevelStatVO> prizeLevelList;

    @Schema(description = "数据一致性体检告警")
    private List<String> issueList;

    /**
     * 一个奖级的中奖情况
     */
    @Data
    public static class PrizeLevelStatVO {

        @Schema(description = "奖级")
        private Integer prizeLevel;

        @Schema(description = "奖品编码")
        private String prizeCode;

        @Schema(description = "奖品名称，奖品已删除时为 null")
        private String prizeName;

        @Schema(description = "奖品类型")
        private String prizeType;

        @Schema(description = "中奖注数")
        private Long winCount;

        @Schema(description = "占全部中奖的比例")
        private BigDecimal winShare;

        @Schema(description = "已发放价值 = 注数 × 单价")
        private BigDecimal issuedValue;
    }
}
