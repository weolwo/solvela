package solvela.ledger.wallet.domain.form;

import solvela.base.common.domain.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 会员钱包表 分页查询表单
 *
 * @Author weolwo
 * @Date 2026-04-18 23:56:48
 * @Copyright weolwo
 */

@Data
@EqualsAndHashCode(callSuper = false)
public class MemberWalletQueryForm extends PageParam {

    /**
     * 会员号。
     *
     * <p>🔴 这里<b>不能</b>再收账号：钱包表已经没有 member_name 列了，
     * 而单据类那几张表虽然还留着，那是<b>展示快照且身上没有任何索引</b> ——
     * 拿它做查询条件必是全表扫。按账号找人请先经 {@code MemberService} 换成会员号。
     */
    @Schema(description = "会员号")
    private Long memberId;

    @Schema(description = "资产类型：SCORE-积分, BALANCE-现金")
    private String assetType;

    @Schema(description = "状态：0-冻结, 1-正常")
    private Integer status;

    @Schema(description = "创建时间")
    private LocalDate createTimeBegin;

    @Schema(description = "创建时间")
    private LocalDate createTimeEnd;

}
