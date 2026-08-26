package solvela.ledger.wallet.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.util.List;

import lombok.Data;
import solvela.ledger.transaction.domain.dto.MemberAssetTransactionStatDTO;

/**
 * 钱包统计的<b>结果模型</b>。管理端直接把它作为响应体返回，<b>没有再装配一层 VO</b>。
 *
 * <h3>为什么这里和列表查询的处理不一样</h3>
 * 列表的 VO 是「某个端决定给出去哪些字段」——{@code MemberWalletDTO} 里有乐观锁版本号和
 * 运营账号，C 端一个都不该看到，所以必须在端上投影一层。
 * 而统计的产物是<b>算出来的结果</b>（含 {@code issueList} 这种给运营看的体检人话），
 * 它本来就不是任何一个端的响应体形状；C 端将来要看自己的资产汇总，会是另一套指标，
 * 不是这份的子集。为它复制一份字段完全相同的 VO，是纯仪式。
 *
 * <h3>而且那层装配会引入一个静默故障</h3>
 * 本类含嵌套 List（如 {@code assetList}）。{@code SolvelaBeanUtil.copy} 底层是
 * Spring 的 {@code BeanUtils.copyProperties}，它会解析泛型：发现
 * {@code List<AssetFlowDTO>} 与 {@code List<AssetFlowVO>} 不兼容后<b>直接跳过该属性</b>，
 * 既不报错也不转换 —— 目标字段留在 {@code null}。
 * 编译通过、接口照常返回，只是那一段数据<b>凭空消失了</b>，
 * 而前端拿到 null 往往只是少显示一块，没人会立刻发现。
 * 要做这层装配就必须用 {@code SolvelaBeanUtil.deepCopy} 或逐层 {@code copyList}；
 * 为一层没有收益的装配去冒这个险不值得。
 *
 * <p>⚠️ 保留了 {@code @Schema}：本类<b>直接</b>作为管理端响应体，去掉会让接口文档退化。
 * 这是一个明确的例外，不是遗漏 —— 注解只是文档元数据，并不把形状绑到某个端上。
 */
@Data
public class MemberWalletStatDTO {

    // ---------------- 资产存量（全量，不受时间范围影响） ----------------

    @Schema(description = "钱包账户数（全量）。一个会员一种资产一行，不等于会员数")
    private Long walletCount;

    @Schema(description = "涉及会员数（去重，全量）")
    private Long memberCount;

    @Schema(description = "冻结账户数（全量）：status=0")
    private Long frozenCount;

    @Schema(description = "资产维度存量，余额按资产类型分开算")
    private List<AssetBalanceDTO> assetList;

    // ---------------- 本期变动（跟时间范围走，数据来自交易明细表） ----------------

    /**
     * 与交易明细页共用同一个 VO 与同一条 SQL —— 那边改口径，这边自动跟着改。
     */
    @Schema(description = "本期资产变动：收入/支出/净额，按资产类型分开算")
    private List<MemberAssetTransactionStatDTO.AssetFlowDTO> flowList;

    @Schema(description = "数据一致性体检告警")
    private List<String> issueList;

    /**
     * 一种资产的存量。<b>余额只在同一 assetType 内可加</b> ——
     * 积分和现金加出来的那个数没有任何含义。
     */
    @Data
    public static class AssetBalanceDTO {

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
