package net.lab1024.sa.ledger.transaction.domain.form;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 交易明细表 新建表单
 *
 * @Author weolwo
 * @Date 2026-04-18 23:49:03
 * @Copyright weolwo
 */

@Data
public class MemberAssetTransactionAddForm {

    @Schema(description = "租户ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "租户ID 不能为空")
    private String tenantId;

    @Schema(description = "会员名", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "会员名 不能为空")
    private String memberName;

    @Schema(description = "资产类型：SCORE, BALANCE", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "资产类型：SCORE, BALANCE 不能为空")
    private String assetType;

    @Schema(description = "资金流向：1-收入, 2-支出", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "资金流向：1-收入, 2-支出 不能为空")
    private Integer transactionType;

    @Schema(description = "变动绝对值", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "变动绝对值 不能为空")
    private BigDecimal changeAmount;

    @Schema(description = "变动后最新余额", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "变动后最新余额 不能为空")
    private BigDecimal balanceAfter;

    @Schema(description = "业务类型：TASK_PRIZE, CONSUME, MANUAL_ADJUST", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "业务类型：TASK_PRIZE, CONSUME, MANUAL_ADJUST 不能为空")
    private String bizType;

    @Schema(description = "关联外部业务ID(如 prize_code)", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "关联外部业务ID(如 prize_code) 不能为空")
    private String bizRefId;

    @Schema(description = "C端展示摘要")
    private String remark;

    @Schema(description = "创建人")
    private String createBy;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

}