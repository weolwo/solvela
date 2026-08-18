package sa.ledger.transaction.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.util.List;
import lombok.Data;

/**
 * 交易明细统计：这段时间里<b>钱是怎么进出的</b>。
 *
 * <p>这张表是账务域唯一的资金流水，钱包余额的每一次变动都在这里留了一行。
 * 但原先的页面只有一张流水表，翻十页也答不出财务每天要问的：
 * 今天净发出去多少、钱都花在哪个业务上、有没有人在手工调账。
 *
 * <h3>三条必须守住的口径</h3>
 * <ol>
 *   <li><b>金额一律按 asset_type 分开</b>：积分和现金不是同一个量纲，
 *       加出来的那个「总金额」没有任何含义；</li>
 *   <li><b>方向只看 transaction_type</b>：{@code change_amount} 存的是<b>绝对值</b>，
 *       收入和支出在数值上都是正数，把它们直接 SUM 出来的数字既不是收入也不是支出；</li>
 *   <li><b>业务分布只给笔数，不给金额</b>：同一个 biz_type 下可能既有积分又有现金，
 *       要给金额就得再按资产类型拆一层，页面上会宽到没法看。
 *       这是刻意的取舍 —— 与其给一个跨量纲相加的假金额，不如只给笔数。</li>
 * </ol>
 *
 * @Author alaric
 * @Date 2026-08-18
 */
@Data
public class MemberAssetTransactionStatVO {

    @Schema(description = "时间范围内的流水笔数")
    private Long txCount;

    @Schema(description = "涉及会员数（去重）")
    private Long memberCount;

    /**
     * 人工调账笔数。系统自己发的奖不需要人插手，这一类是<b>有人手工改了别人的钱</b>，
     * 量再小也该有人看一眼 —— 财务对账时第一个要查的就是它。
     */
    @Schema(description = "人工调账笔数：biz_type = MANUAL_ADJUST")
    private Long manualAdjustCount;

    @Schema(description = "资产维度收支，金额按资产类型分开算")
    private List<AssetFlowVO> assetList;

    @Schema(description = "业务类型分布（TOP 10），只给笔数")
    private List<BizTypeStatVO> bizTypeList;

    @Schema(description = "数据一致性体检告警")
    private List<String> issueList;

    /**
     * 一种资产在时间范围内的收支。<b>只在同一 assetType 内可加。</b>
     */
    @Data
    public static class AssetFlowVO {

        @Schema(description = "资产类型：SCORE/BALANCE")
        private String assetType;

        @Schema(description = "收入笔数：transaction_type=1")
        private Long incomeCount;

        @Schema(description = "收入金额")
        private BigDecimal incomeAmount;

        @Schema(description = "支出笔数：transaction_type=2")
        private Long expenseCount;

        @Schema(description = "支出金额")
        private BigDecimal expenseAmount;

        /**
         * 净额 = 收入 - 支出。为负说明这段时间用户手上的这种资产是净减少的。
         */
        @Schema(description = "净额 = 收入金额 - 支出金额，可能为负")
        private BigDecimal netAmount;
    }

    /**
     * 一类业务的流水笔数。
     *
     * <p>⚠️ {@code biz_type} <b>没有封闭字典</b>：它是调用方随手传进来的字符串
     * （{@code executeWalletDeduct/Refund} 的入参），DDL 列注释写的
     * 「TASK_PRIZE, CONSUME, MANUAL_ADJUST」和实际写入的值已经对不上了 ——
     * 派奖链路落的是 {@code PROPOSAL_REWARD}，三个里一个都不是。
     * 所以这里原样回显取值，不做归类、也不用「其它」把它盖住。
     */
    @Data
    public static class BizTypeStatVO {

        @Schema(description = "业务类型原值")
        private String bizType;

        @Schema(description = "笔数")
        private Long txCount;

        @Schema(description = "涉及会员数")
        private Long memberCount;

        @Schema(description = "占全部笔数的比例")
        private BigDecimal txShare;
    }
}
