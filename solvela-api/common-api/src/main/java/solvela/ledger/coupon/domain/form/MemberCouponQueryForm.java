package solvela.ledger.coupon.domain.form;

import solvela.base.domain.PageParam;
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

    /**
     * 会员号（精确匹配，走 member_id 索引）。
     *
     * <p>🔴 这里<b>刻意不再收账号</b>：v3.71.0 之后 {@code member_name} 只是展示快照、
     * 身上没有任何索引，拿它当查询条件必是全表扫；而它又是可改的，
     * 用户改名之后按旧名字查等于查不到 —— 「不报错，只是查不到了」正是这次换键要消灭的。
     * 后台要按账号找人，先经 {@code MemberService.getMemberId} 换成会员号。
     */
    @Schema(description = "会员号")
    private Long memberId;

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
