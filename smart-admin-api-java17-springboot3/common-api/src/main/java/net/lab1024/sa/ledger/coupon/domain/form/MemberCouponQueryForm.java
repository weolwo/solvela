package net.lab1024.sa.ledger.coupon.domain.form;

import net.lab1024.sa.base.common.domain.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 会员优惠券 分页查询表单
 *
 * @Author weolwo
 * @Date 2026-04-18 23:42:44
 * @Copyright weolwo
 */

@Data
@EqualsAndHashCode(callSuper = false)
public class MemberCouponQueryForm extends PageParam {

    @Schema(description = "租户ID")
    private String tenantId;

    @Schema(description = "会员名")
    private String memberName;

    @Schema(description = "券模编码")
    private String couponCode;

    @Schema(description = "有效期开始")
    private LocalDate validStartTimeBegin;

    @Schema(description = "有效期开始")
    private LocalDate validStartTimeEnd;

    @Schema(description = "券名称")
    private String couponName;

    @Schema(description = "券类型")
    private String couponType;

    @Schema(description = "状态：0-未使用, 1-已使用, 2-已过期, 3-已作废")
    private Integer status;

}
