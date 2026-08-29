package solvela.ledger.transaction.domain.dto;

import solvela.enums.TransactionTypeEnum;
import java.math.BigDecimal;
import java.time.LocalDateTime;

import lombok.Data;

/**
 * 资产流水列表的<b>读模型</b>：mapper 的 resultMap 映射到这里，service 也返回它。
 *
 * <p>{@code createBy} / {@code updateBy} 是<b>后台运营人员</b>的账号，
 * C 端接口一个都不该看到 —— DTO 是领域能查出来的全部，VO 是某个端决定给出去的那一部分，
 * 装配在端上做。完整说明见 {@code MemberWalletDTO}。
 */

@Data
public class MemberAssetTransactionDTO {


    private Long id;

    /**
     * 会员号
     */
    private Long memberId;

    /**
     * 账号 —— <b>落库时的展示快照</b>，不是会员当前的账号。
     * 会员改名之后这里仍是改名前的值，这是刻意的：单据回答的是「当时是谁」。
     */
    private String memberName;

    /**
     * 资产类型：SCORE, BALANCE
     */
    private String assetType;

    /**
     * 资金流向：1-收入, 2-支出
     */
    private TransactionTypeEnum transactionType;

    /**
     * 变动绝对值
     */
    private BigDecimal changeAmount;

    /**
     * 变动后最新余额
     */
    private BigDecimal balanceAfter;

    /**
     * 业务类型：TASK_PRIZE, CONSUME, MANUAL_ADJUST
     */
    private String bizType;

    /**
     * 关联外部业务ID(如 prize_code)
     */
    private String bizRefId;

    /**
     * C端展示摘要
     */
    private String remark;

    /**
     * 创建人
     */
    private String createBy;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新人
     */
    private String updateBy;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;

}
