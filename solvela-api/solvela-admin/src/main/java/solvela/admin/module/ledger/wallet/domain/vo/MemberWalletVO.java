package solvela.admin.module.ledger.wallet.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import lombok.Data;

/**
 * 会员钱包表 列表VO
 *
 * @Author weolwo
 * @Date 2026-04-18 23:56:48
 * @Copyright weolwo
 */

@Data
public class MemberWalletVO {


    @Schema(description = "id")
    private Long id;

    @Schema(description = "会员号")
    private Long memberId;

    /**
     * 账号，join {@code t_member} 取的<b>当前值</b>，不是快照 —— 钱包表里没有这一列。
     * 会员被注销/清理时可能为空，前端要能接受空值。
     */
    @Schema(description = "会员账号（取自会员主表的当前值）")
    private String memberName;

    @Schema(description = "资产类型：SCORE-积分, BALANCE-现金")
    private String assetType;

    @Schema(description = "余额")
    private BigDecimal balance;

    @Schema(description = "状态：0-冻结, 1-正常")
    private Integer status;

    @Schema(description = "乐观锁版本号")
    private Integer version;

    @Schema(description = "创建人")
    private String createBy;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    @Schema(description = "更新人")
    private String updateBy;

    @Schema(description = "更新时间")
    private LocalDateTime updateTime;

}
