package solvela.ledger.logistic.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.util.List;

import lombok.Data;

/**
 * 发货物流统计：今天新增了多少履约单，以及<b>手上到底积压着多少单没发出去</b>。
 *
 * <h3>积压必须看全量，不能跟着「今天」走</h3>
 * 「今日新增」跟时间范围走是对的；但<b>待发货积压是存量</b> ——
 * 把它限制在今天，压了三天的那些单子会正好从页面上消失，
 * 而那恰恰是这个页面唯一需要有人动手的东西。所以积压那一组数字明确标注为全量。
 *
 * <h3>「待发货」里有两种，性质完全不同</h3>
 * 实物是三段式履约（见 {@code PhysicalAssetHandler}）：中奖时用户还没填地址，
 * 履约单先落、收件信息后补。所以 status=0 同时装着两类单子：
 * <ul>
 *   <li><b>收件信息还没补全</b> —— 想发也发不了，要去催用户，不是运营的锅；</li>
 *   <li><b>地址齐了等发货</b> —— 这才是今天该发出去的活。</li>
 * </ul>
 * 光看一个「待发货 N 单」分不出这两种，运营只能一单一单点开看。
 * 这两个数是本页最有用的东西，{@code DeliveryStatusEnum} 的类注释也早就写明了
 * 「要区分得看 receiver_address 是否为空，不是靠状态」。
 *
 * @Author alaric
 * @Date 2026-08-18
 */
@Data
public class PhysicalDeliveryStatVO {

    // ---------------- 本期新增（时间窗落在 create_time） ----------------

    @Schema(description = "本期新增履约单数")
    private Long newCount;

    @Schema(description = "本期新增涉及的会员数（去重）")
    private Long newMemberCount;

    // ---------------- 履约状态（全量，不受时间范围影响） ----------------

    @Schema(description = "履约单总数（全量）")
    private Long totalCount;

    @Schema(description = "待发货（全量）：status=0")
    private Long pendingCount;

    /**
     * 待发货里<b>收件信息还没补全</b>的单数。想发也发不了 —— 要去催用户填地址。
     */
    @Schema(description = "待发货且收件信息不全（全量）：收件人/电话/地址任一为空")
    private Long pendingNoAddressCount;

    /**
     * 待发货里<b>地址齐了、就等发货</b>的单数。这才是运营今天真正能干的活。
     */
    @Schema(description = "待发货且收件信息齐全（全量）：可以直接发的单")
    private Long pendingReadyCount;

    /**
     * 最久的一单待发货已经等了多少分钟。只看条数看不出「压了一周」。
     */
    @Schema(description = "最久一单待发货已等待的分钟数，无积压时为 0")
    private Long pendingOldestMinutes;

    @Schema(description = "已发货（全量）：status=1")
    private Long deliveredCount;

    @Schema(description = "已签收（全量）：status=2")
    private Long signedCount;

    @Schema(description = "异常退回（全量）：status=3，终态，需人工跟进")
    private Long returnedCount;

    /**
     * 已作废（软删除）。页面上的「删除」按钮走的是
     * {@code UPDATE ... SET status=-1 WHERE status=0}，数据并没有真的删掉。
     *
     * <p>⚠️ 这个取值<b>既不在 DDL 的列注释里，也不在 {@code DeliveryStatusEnum} 里</b>，
     * 只有前端字典认得它。不单独给它一个桶的话，四个状态桶之和会小于总数，
     * 看的人只会以为统计算错了。
     */
    @Schema(description = "已作废（全量）：status=-1，页面「删除」的实际效果，是软删除")
    private Long discardedCount;

    /**
     * 有效履约单数 = 总数 - 已作废。发货率的分母用它 ——
     * 作废掉的单子是被主动撤回的，不该算成「没发出去」拉低发货率。
     */
    @Schema(description = "有效履约单数（全量）= 总数 - 已作废")
    private Long validCount;

    @Schema(description = "发货率 = (已发货 + 已签收) / 有效履约单数")
    private BigDecimal deliveredRate;

    @Schema(description = "来源维度分布（全量），按履约单数降序")
    private List<SourceStatVO> sourceList;

    @Schema(description = "数据一致性体检告警")
    private List<String> issueList;

    /**
     * 一个来源的履约情况。
     *
     * <p>{@code source_type} 这一列同样没有枚举约束，原样回显取值，不做归类。
     */
    @Data
    public static class SourceStatVO {

        @Schema(description = "来源类型原值")
        private String sourceType;

        @Schema(description = "履约单数（全量）")
        private Long deliveryCount;

        @Schema(description = "其中待发货的单数")
        private Long pendingCount;

        @Schema(description = "其中已作废的单数：status=-1")
        private Long discardedCount;

        @Schema(description = "发货率 = (已发货 + 已签收) / (履约单数 - 已作废)")
        private BigDecimal deliveredRate;
    }
}
