package net.lab1024.sa.ledger.transaction.domain.form;

import net.lab1024.sa.base.common.domain.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 交易明细表 分页查询表单
 *
 * @Author weolwo
 * @Date 2026-04-18 23:49:03
 * @Copyright weolwo
 */

@Data
@EqualsAndHashCode(callSuper = false)
public class MemberAssetTransactionQueryForm extends PageParam {

    @Schema(description = "租户ID")
    private String tenantId;

    @Schema(description = "会员名")
    private String memberName;

    @Schema(description = "资产类型：SCORE, BALANCE")
    private String assetType;

    @Schema(description = "资金流向：1-收入, 2-支出")
    private Integer transactionType;

    @Schema(description = "关联外部业务ID(如 prizeCode)")
    private String bizRefId;

    @Schema(description = "创建时间")
    private LocalDate createTimeBegin;

    @Schema(description = "创建时间")
    private LocalDate createTimeEnd;

}
