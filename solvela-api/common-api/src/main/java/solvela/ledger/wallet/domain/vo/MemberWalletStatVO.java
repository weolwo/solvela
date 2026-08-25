package solvela.ledger.wallet.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.util.List;
import lombok.Data;
import solvela.ledger.transaction.domain.vo.MemberAssetTransactionStatVO;

/**
 * 钱包统计：用户手上<b>现在</b>一共有多少资产，以及这段时间<b>变动了多少</b>。
 *
 * <h3>为什么这一页的「余额」不跟时间范围走</h3>
 * 钱包表是<b>存量表</b>：一个会员一种资产一行，只有一个当前余额，没有历史切片。
 * 「今天的总余额」这个说法本身就不成立 —— 按 {@code create_time} 去筛，
 * 筛出来的是「今天新开的钱包」，那几个账户的余额加起来既不是今天发出去的钱，
 * 也不是用户现在手上的钱，是一个<b>什么都不回答</b>的数字。
 *
 * <p>所以这一页把两件事明确分开：
 * <ul>
 *   <li><b>资产存量</b>（{@link #assetList}）：全量，不受时间范围影响 —— 用户手上现在有多少；</li>
 *   <li><b>本期变动</b>（{@link #flowList}）：跟时间范围走，数据来自交易明细表 ——
 *       这段时间进出了多少、净增净减多少。</li>
 * </ul>
 * 「本期变动」直接复用交易明细页的那条 SQL，不在钱包这边另写一份：
 * 同一个口径出现两处实现，早晚会漂成两个对不上的数。
 *
 * @Author alaric
 * @Date 2026-08-18
 */
@Data
public class MemberWalletStatVO {

    // ---------------- 资产存量（全量，不受时间范围影响） ----------------

    @Schema(description = "钱包账户数（全量）。一个会员一种资产一行，不等于会员数")
    private Long walletCount;

    @Schema(description = "涉及会员数（去重，全量）")
    private Long memberCount;

    @Schema(description = "冻结账户数（全量）：status=0")
    private Long frozenCount;

    @Schema(description = "资产维度存量，余额按资产类型分开算")
    private List<AssetBalanceVO> assetList;

    // ---------------- 本期变动（跟时间范围走，数据来自交易明细表） ----------------

    /**
     * 与交易明细页共用同一个 VO 与同一条 SQL —— 那边改口径，这边自动跟着改。
     */
    @Schema(description = "本期资产变动：收入/支出/净额，按资产类型分开算")
    private List<MemberAssetTransactionStatVO.AssetFlowVO> flowList;

    @Schema(description = "数据一致性体检告警")
    private List<String> issueList;

    /**
     * 一种资产的存量。<b>余额只在同一 assetType 内可加</b> ——
     * 积分和现金加出来的那个数没有任何含义。
     */
    @Data
    public static class AssetBalanceVO {

        @Schema(description = "资产类型：SCORE/BALANCE")
        private String assetType;

        @Schema(description = "账户数")
        private Long walletCount;

        @Schema(description = "余额合计（全量）")
        private BigDecimal totalBalance;

        @Schema(description = "人均余额 = 余额合计 / 账户数")
        private BigDecimal avgBalance;

        /**
         * 冻结账户里压着的余额。钱还在账上，但用户取不出来也用不了 ——
         * 冻结久了就是客诉。
         */
        @Schema(description = "冻结账户中的余额合计")
        private BigDecimal frozenBalance;
    }
}
