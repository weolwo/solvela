package net.lab1024.sa.risk.proposal.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.util.List;
import lombok.Data;

/**
 * 提案漏斗：把一堆提案行压成「这笔钱走到哪一步了」。
 *
 * <p>提案表是<b>钱出去的必经之路</b>（提案 → 风控 → 审批 → 执行 → 到账），
 * 而原先的记录页是 22 列裸字段、零聚合，翻十页也答不出运营每天最该问的三件事：
 * 今天发出去多少、有没有单子压在审批池里没人管、有没有钱卡在半路没到用户手上。
 *
 * <h3>三个「没人查就发现不了」的数字</h3>
 * <ol>
 *   <li><b>审批积压</b>（10-待一审 / 11-待二审）：提案停在这里就是用户的奖没发。
 *       这里同时给出最久的一条已经等了多少分钟 —— 只看条数看不出「压了三天」；</li>
 *   <li><b>卡在下发</b>（30-待执行 / 40-执行中 且很久没动过）：下发是在提案事务提交后
 *       同步调起的，中途进程退出就没有第二次机会 ——
 *       <b>全工程没有任何重试/补偿的定时任务</b>，这些提案会永远停在半路，
 *       钱既没发出去也没标成失败；</li>
 *   <li><b>一审二审同一个人</b>：审批接口不校验两级审批人是否同一人，
 *       双层审批一旦变成同一个人点两次，这道防线就只是形式。</li>
 * </ol>
 *
 * <p>⚠️ <b>金额一律按 asset_type 分开算，绝不合并成一个「总金额」</b>：
 * 积分、现金、券张数、实物件数不是同一个量纲，加在一起的那个数字没有任何含义。
 *
 * @Author alaric
 * @Date 2026-08-16
 */
@Data
public class ProposalFunnelVO {

    @Schema(description = "提案总数（筛选范围内）")
    private Long totalCount;

    @Schema(description = "涉及会员数（去重）")
    private Long memberCount;

    // ---------------- 各状态分布 ----------------

    /**
     * 0-等待中。{@code addProposal} 只会落 10 / 30 / 80 三种初始状态，正常链路<b>不产生 0</b>，
     * 出现即说明有人绕过提案链路直接插库（后台「新建」按钮就能做到）。
     */
    @Schema(description = "等待中：status=0，正常链路不会产生")
    private Long waitingCount;

    @Schema(description = "待一审：status=10")
    private Long firstReviewCount;

    @Schema(description = "待二审：status=11")
    private Long secondReviewCount;

    @Schema(description = "驳回：status=20")
    private Long rejectedCount;

    @Schema(description = "待执行：status=30")
    private Long pendingExecuteCount;

    @Schema(description = "执行中：status=40")
    private Long executingCount;

    @Schema(description = "成功：status=50")
    private Long successCount;

    @Schema(description = "部分成功：status=60，发了一半，需要人工补齐")
    private Long partialCount;

    @Schema(description = "彻底失败：status=70")
    private Long failedCount;

    @Schema(description = "风控拦截：status=80，钱没出去，是防线生效")
    private Long blockedCount;

    // ---------------- 关键比率与积压 ----------------

    @Schema(description = "到账率 = 成功 / 提案总数")
    private BigDecimal successRate;

    @Schema(description = "风控拦截率 = 风控拦截 / 提案总数")
    private BigDecimal blockRate;

    @Schema(description = "待审积压条数 = 待一审 + 待二审")
    private Long pendingReviewCount;

    /**
     * 待审积压里最久的一条已经等了多少分钟。只看条数看不出「压了三天」。
     */
    @Schema(description = "最久一条待审提案已等待的分钟数，无积压时为 0")
    private Long pendingReviewOldestMinutes;

    /**
     * 卡在下发链路的条数：30-待执行 / 40-执行中 且超过 30 分钟没有任何更新。
     * 没有任何重试任务会来救它们。
     */
    @Schema(description = "卡在下发：status IN (30,40) 且 30 分钟内无更新")
    private Long stuckDispatchCount;

    // ---------------- 分布 ----------------

    @Schema(description = "资产维度发放情况，金额按资产类型分开算")
    private List<AssetStatVO> assetList;

    @Schema(description = "来源维度分布，按提案数降序")
    private List<SourceStatVO> sourceList;

    @Schema(description = "风控拦截原因分布（TOP 10）")
    private List<BlockReasonVO> blockReasonList;

    @Schema(description = "数据一致性与流程体检告警")
    private List<String> issueList;

    /**
     * 一种资产的发放情况。<b>金额只在同一 assetType 内可加</b>。
     */
    @Data
    public static class AssetStatVO {

        /**
         * 只回编码，中文名由前端的 ASSET_TYPE_ENUM 解析 ——
         * {@code PrizeTypeEnum} 是个裸枚举（没有 desc），在这里再手写一份字典就是第二个真相源。
         */
        @Schema(description = "资产类型：SCORE/BALANCE/COUPON/PHYSICAL")
        private String assetType;

        @Schema(description = "提案条数")
        private Long proposalCount;

        @Schema(description = "成功条数")
        private Long successCount;

        @Schema(description = "已发出金额/数量 = SUM(amount × quantity)，仅统计成功的")
        private BigDecimal successAmount;

        @Schema(description = "在途金额：待审 + 待执行 + 执行中，钱还没出去但已被占用")
        private BigDecimal pendingAmount;

        @Schema(description = "被风控拦下的金额")
        private BigDecimal blockedAmount;
    }

    /**
     * 一个来源的提案与到账情况
     */
    @Data
    public static class SourceStatVO {

        @Schema(description = "来源编码：TASK/DRAW/LOTTERY/MANUAL")
        private String sourceType;

        @Schema(description = "来源说明；不在字典内时回显原值")
        private String sourceDesc;

        @Schema(description = "提案条数")
        private Long proposalCount;

        @Schema(description = "成功条数")
        private Long successCount;

        @Schema(description = "到账率")
        private BigDecimal successRate;

        /**
         * 是否是字典外的取值。历史上四个发奖 handler 都硬编码写了
         * {@code LOTTERY_DRAW}，任务发的奖也被记成彩票抽奖 —— 那批历史数据就落在这里。
         */
        @Schema(description = "来源编码是否不在 ProposalSourceTypeEnum 字典内")
        private Boolean unknownSource;
    }

    /**
     * 一类风控拦截原因。
     *
     * <p>按 {@code risk_code}（取值封闭，见 {@code RiskBlockCode}）聚类，而不是按 remark 文案 ——
     * 话术会改、也早晚会带上具体数值，按文案聚类的统计迟早会悄悄裂成两条。
     * 归不了类的历史行（回填规则没覆盖到的文案）保留原文展示，不用「其它」把它盖掉。
     */
    @Data
    public static class BlockReasonVO {

        @Schema(description = "拦截分类编码，对齐 RiskBlockCode；历史数据可能为 null")
        private String riskCode;

        @Schema(description = "拦截原因：能归类的取分类说明，归不了类的回显 remark 原文")
        private String reason;

        @Schema(description = "条数")
        private Long blockCount;

        @Schema(description = "占全部拦截的比例")
        private BigDecimal blockShare;

        /**
         * 是否需要人介入。防刷拦得再多也不用管，
         * 「单次金额超限（系统兜底被触发）」和「预算已耗尽」哪怕几条都该有人看。
         * 判据收在 {@code RiskBlockCode.needsAttention()} 一处。
         */
        @Schema(description = "是否需要人介入排查")
        private Boolean needsAttention;
    }
}
