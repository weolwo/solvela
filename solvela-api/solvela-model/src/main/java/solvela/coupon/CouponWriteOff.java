package solvela.coupon;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import solvela.enums.CouponDeductTargetEnum;
import solvela.enums.CouponWriteOffActionEnum;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 优惠券核销流水 实体类。<b>只增不改。</b>
 *
 * <h3>🔴 它补的是一个会在上线三个月后才暴露的洞</h3>
 * 只在 {@code t_member_coupon} 上改状态的话，<b>状态列只能表达「现在是什么」，
 * 表达不了「发生过什么」</b>：券被锁定、订单取消、券释放回去 ——
 * 那次锁定的痕迹一点不剩。用户来问「我的券刚才还能用，现在怎么回事」，查无对证。
 *
 * <p>更要命的是 {@link #discountAmount}：规则是「8 折最高减 50」，
 * <b>真正减了多少取决于订单金额</b>。不落地的话退款不知道该退多少、
 * 「本期核销金额」算不出来、财务对不了账。
 *
 * <h3>三个动作各记一行，不是只记核销成功那次</h3>
 * 只记 {@code CONFIRM} 的话行数少一半，但「锁了又释放」就查不到了 ——
 * 而券的纠纷恰恰大多发生在那个窗口里。
 *
 * <p>量级完全撑得住：百万张券、三成核销率也就三十万次 × 3 行，
 * 和 {@code t_member_notification} 那张亿级表不是一个数量级。
 *
 * @Author alaric
 * @Date 2026-09-15
 * @Copyright weolwo
 */
@Data
@TableName("t_coupon_write_off")
public class CouponWriteOff {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long couponId;

    /** 会员号。冗余一列，按人查时不用回表 */
    private Long memberId;

    /** LOCK / CONFIRM / RELEASE */
    private CouponWriteOffActionEnum action;

    /** MALL-商城订单 / EXTERNAL-外部场景 */
    private String bizType;

    /** 订单号 / 外部单号 */
    private String bizRefId;

    /** 外部场景码，如 MOBILE_RECHARGE。{@code bizType=MALL} 时为空 */
    private String sceneCode;

    /** 抵扣前应付 */
    private BigDecimal originalAmount;

    /**
     * 🔴 本次实际抵扣额。
     *
     * <p>退款要按它退、财务要按它对账、「本期核销金额」要按它算。
     * 这是本表存在的首要理由。
     */
    private BigDecimal discountAmount;

    /** CASH / SCORE 快照 */
    private CouponDeductTargetEnum deductTarget;

    /** 释放原因等 */
    private String remark;

    /** 操作人。人工核销才有值，系统核销为空 */
    private String createBy;

    private LocalDateTime createTime;
}
