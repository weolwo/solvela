package solvela.stat.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 发奖健康度（大屏 C 位）
 *
 * <p>🔴 <b>「发了多少条」与「发出多少价值」是两个数，必须并列展示</b>（方案 §2.1）：
 * 实测某活动 315 条发奖记录里，除了券之外一分钱都没真正发出去 ——
 * 只写「累计发奖 315 次」会让运营以为活动跑得很好。
 *
 * <p>🔴 <b>三个状态数之和必须等于 total</b>。原型 v2 曾在这里漏算过 100 条零值短路成功的记录，
 * 315 ≠ 51+100+64 —— 而那张卡正是用来演示本条口径的。
 *
 * <p>🔴 <b>末端是「已发出」不是「已到账」</b>（方案 §2.2）：营销域只知道自己发出了发奖指令，
 * 钱有没有到用户手上是账务域的事，界面措辞不能混。
 *
 * @Author weolwo
 * @Date 2026-08-03
 */
@Data
public class PrizeHealthDTO {

    @Schema(description = "统计口径：全局 or 某活动。activityCode 为空时是全局")
    private String activityCode;

    @Schema(description = "统计天数（趋势用；条数与价值是该活动/全局的全量，不受 days 限制）")
    private Integer days;

    @Schema(description = "发奖记录总条数")
    private Integer total;

    @Schema(description = "成功条数 status=1")
    private Integer successCount;

    @Schema(description = "失败条数 status=2")
    private Integer failedCount;

    @Schema(description = "等待条数 status=0（正常只应对应「等人工审批」）")
    private Integer waitingCount;

    @Schema(description = "待审批积压笔数 approve_status=1（营销域审批，不含账务侧）")
    private Integer pendingApproveCount;

    @Schema(description = "待审批最长滞留小时数")
    private Long maxWaitHours;

    @Schema(description = "已发出价值合计：仅 status=1 计入")
    private BigDecimal issuedValue;

    @Schema(description = "按资产类型拆开的条数与价值，count 之和等于 total")
    private List<AssetItem> byAssetList;

    @Schema(description = "发奖失败原因 TOP")
    private List<FailReason> failReasonList;

    @Schema(description = "日期轴，已补齐无数据的日期")
    private List<String> dateList;

    @Schema(description = "发出价值趋势：日期 × 资产类型，已补 0")
    private List<TrendItem> trendList;

    @Data
    @Schema(description = "按资产类型拆分")
    public static class AssetItem {

        @Schema(description = "资产类型 SCORE/BALANCE/COUPON/PHYSICAL")
        private String prizeType;

        @Schema(description = "记录条数（含成功、失败、等待）")
        private Integer count;

        @Schema(description = "已发出价值：仅 status=1 计入")
        private BigDecimal issuedValue;
    }

    @Data
    @Schema(description = "失败原因")
    public static class FailReason {

        @Schema(description = "失败原因原文")
        private String failReason;

        @Schema(description = "条数")
        private Integer count;
    }

    @Data
    @Schema(description = "价值趋势的一个点")
    public static class TrendItem {

        @Schema(description = "日期 yyyy-MM-dd")
        private String statDate;

        @Schema(description = "资产类型")
        private String prizeType;

        @Schema(description = "当天该资产已发出价值")
        private BigDecimal issuedValue;
    }
}
