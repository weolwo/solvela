package net.lab1024.sa.ledger.wallet.domain.vo;

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

    @Schema(description = "租户ID")
    private String tenantId;

    @Schema(description = "会员名")
    private String memberName;

    @Schema(description = "积分余额")
    private BigDecimal scoreBalance;

    @Schema(description = "现金余额")
    private BigDecimal cashBalance;

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
