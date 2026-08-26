package solvela.ledger.transaction.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.util.List;

import lombok.Data;

/**
 * 资产流水统计的<b>结果模型</b>。管理端直接把它作为响应体返回，<b>没有再装配一层 VO</b>。
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
public class MemberAssetTransactionStatDTO {

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
    private List<AssetFlowDTO> assetList;

    @Schema(description = "业务类型分布（TOP 10），只给笔数")
    private List<BizTypeStatDTO> bizTypeList;

    @Schema(description = "数据一致性体检告警")
    private List<String> issueList;

    /**
     * 一种资产在时间范围内的收支。<b>只在同一 assetType 内可加。</b>
     */
    @Data
    public static class AssetFlowDTO {

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
    public static class BizTypeStatDTO {

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
