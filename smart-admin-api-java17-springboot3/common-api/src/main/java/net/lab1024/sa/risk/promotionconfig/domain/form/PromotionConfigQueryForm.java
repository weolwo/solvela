package net.lab1024.sa.risk.promotionconfig.domain.form;

import net.lab1024.sa.base.common.domain.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 优惠配置表 分页查询表单
 *
 * @Author weolwo
 * @Date 2026-04-18 23:28:25
 * @Copyright weolwo
 */

@Data
@EqualsAndHashCode(callSuper = false)
public class PromotionConfigQueryForm extends PageParam {

    @Schema(description = "租户ID")
    private String tenantId;

    @Schema(description = "优惠配置名称")
    private String promoName;

    @Schema(description = "资产类型：SCORE(积分), BALANCE(现金), COUPON(优惠券), PHYSICAL(实物)")
    private String prizeType;

    @Schema(description = "创建时间")
    private LocalDate createTimeBegin;

    @Schema(description = "创建时间")
    private LocalDate createTimeEnd;

    @Schema(description = "同周期内，单会员ID最多领取次数 (-1为不限)")
    private Integer identifyLimit;

    @Schema(description = "同周期内，单手机号最多领取次数 (-1为不限)")
    private Integer phoneLimit;

    @Schema(description = "同周期内，单IP地址最多领取次数 (-1为不限)")
    private Integer ipLimit;

    @Schema(description = "状态：0-停用, 1-启用")
    private Integer status;

}
