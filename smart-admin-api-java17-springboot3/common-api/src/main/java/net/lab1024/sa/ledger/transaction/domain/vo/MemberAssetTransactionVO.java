package net.lab1024.sa.ledger.transaction.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 交易明细表 列表VO
 *
 * @Author weolwo
 * @Date 2026-04-18 23:49:03
 * @Copyright weolwo
 */

@Data
public class MemberAssetTransactionVO {


    @Schema(description = "id")
    private Long id;

    @Schema(description = "租户ID")
    private String tenantId;

    @Schema(description = "会员名")
    private String memberName;

    @Schema(description = "资产类型：SCORE, BALANCE")
    private String assetType;

    @Schema(description = "资金流向：1-收入, 2-支出")
    private Integer transactionType;

    @Schema(description = "变动绝对值")
    private BigDecimal changeAmount;

    @Schema(description = "变动后最新余额")
    private BigDecimal balanceAfter;

    @Schema(description = "业务类型：TASK_PRIZE, CONSUME, MANUAL_ADJUST")
    private String bizType;

    @Schema(description = "关联外部业务ID(如 prize_code)")
    private String bizRefId;

    @Schema(description = "C端展示摘要")
    private String remark;

    @Schema(description = "创建人")
    private String createBy;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    @Schema(description = "更新人")
    private String updateBy;

    @Schema(description = "更新时间")
    private LocalDateTime updateTime;

}
