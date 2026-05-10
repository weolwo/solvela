package net.lab1024.sa.ledger.coupon.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 会员优惠券 列表VO
 *
 * @Author weolwo
 * @Date 2026-04-18 23:42:44
 * @Copyright weolwo
 */

@Data
public class MemberCouponVO {


    @Schema(description = "id")
    private Long id;

    @Schema(description = "租户ID")
    private String tenantId;

    @Schema(description = "会员名")
    private String memberName;

    @Schema(description = "券模编码")
    private String couponCode;

    @Schema(description = "券类型")
    private String couponType;

    @Schema(description = "券名称")
    private String couponName;

    @Schema(description = "状态：0-未使用, 1-已使用, 2-已过期, 3-已作废")
    private Integer status;

    @Schema(description = "来源：DRAW, TASK, MANUAL_SEND")
    private String sourceType;

    @Schema(description = "关联单号")
    private String sourceBizId;

    @Schema(description = "有效期开始")
    private LocalDateTime validStartTime;

    @Schema(description = "有效期结束")
    private LocalDateTime validEndTime;

    @Schema(description = "核销时间")
    private LocalDateTime usedTime;

    @Schema(description = "创建人")
    private String createBy;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    @Schema(description = "更新人")
    private String updateBy;

    @Schema(description = "更新时间")
    private LocalDateTime updateTime;

}
