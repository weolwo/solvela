package solvela.admin.module.ledger.coupon.domain.vo;

import solvela.enums.CouponDeductTargetEnum;
import solvela.enums.CouponDiscountTypeEnum;
import solvela.enums.CouponScopeTypeEnum;
import solvela.enums.CouponStatusEnum;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
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

    @Schema(description = "会员号")
    private Long memberId;

    /**
     * 账号 —— <b>落库时的展示快照</b>，不是会员当前的账号。
     * 会员改名之后这里仍是改名前的值，这是刻意的：单据回答的是「当时是谁」。
     */
    @Schema(description = "会员账号（下单当时的快照）")
    private String memberName;

    @Schema(description = "券模编码")
    private String couponCode;

    @Schema(description = "券类型")
    private String couponType;

    @Schema(description = "券名称")
    private String couponName;

    @Schema(description = "状态：0-未使用, 1-已使用, 2-已过期, 3-已作废")
    private CouponStatusEnum status;

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

    // ------------------------------------------------------------------
    // 规则快照与锁定状态。2026-09-15 补。
    //
    // 🔴 这一层是最后一道、也是最容易漏的一道：表上有列、DTO 上有字段、
    //    mapper 也查出来了，但 VO 没有 —— SolvelaPageUtil 按 VO 的字段拷贝，
    //    多出来的一律丢掉，【不报错】。表现是接口安静地少返回一半字段。
    //
    //    2026-09-15 联调时就卡在这儿：改完 DTO 和 mapper 以为完事了，
    //    页面还是显示不出规则，一路排查到这一层才发现。
    //
    // ⚠️ 刻意【没有】scope_refs：它是一段 json 明细，列表页展示的是 scopeType
    //    那个档位。把它放进响应体只是凭空多一段没人看的 json。
    // ------------------------------------------------------------------

    @Schema(description = "发券时的模板版本。null=发券时没有模板，这张券没有规则")
    private Integer templateVersion;

    @Schema(description = "规则快照：FIXED-固定金额 / PERCENT-百分比。null=没有规则")
    private CouponDiscountTypeEnum discountType;

    @Schema(description = "规则快照：FIXED 时是抵扣额，PERCENT 时是折扣率")
    private BigDecimal discountValue;

    @Schema(description = "规则快照：最低消费门槛。0=无门槛")
    private BigDecimal minAmount;

    @Schema(description = "规则快照：最高抵扣。PERCENT 时必填")
    private BigDecimal maxDiscount;

    @Schema(description = "规则快照：CASH-抵现金 / SCORE-抵积分")
    private CouponDeductTargetEnum deductTarget;

    @Schema(description = "规则快照：适用范围")
    private CouponScopeTypeEnum scopeType;

    @Schema(description = "锁定它的单据号。客服排查「用户说券不见了」时最有用的一列")
    private String lockedBizId;

    @Schema(description = "锁定时间。兜底任务按它判超时")
    private LocalDateTime lockedTime;

    @Schema(description = "本次实际抵扣额。⚠️ 冗余列，权威在 t_coupon_write_off")
    private BigDecimal discountAmount;

    @Schema(description = "创建人")
    private String createBy;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    @Schema(description = "更新人")
    private String updateBy;

    @Schema(description = "更新时间")
    private LocalDateTime updateTime;

}
