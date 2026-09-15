package solvela.ledger;

import solvela.enums.CouponStatusEnum;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

import lombok.Data;

/**
 * 会员优惠券 实体类
 *
 * @Author weolwo
 * @Date 2026-04-18 23:42:44
 * @Copyright weolwo
 */

@Data
@TableName("t_member_coupon")
public class MemberCoupon {

    /**
     * id
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 会员号：关联键（v3.71.0 换键）。查询、join、对账一律用它。
     */
    private Long memberId;

    /**
     * 会员账号 —— <b>展示快照，不是关联键</b>。
     *
     * <p>记的是「写这条记录当时那个账号」，会员改名之后<b>刻意不跟着变</b>：
     * 单据要回答的是「当时是谁」，这和 {@code t_mall_order} 里存商品名快照是同一个模式。
     *
     * <p>🔴 <b>不要拿它做查询条件</b>：这一列身上已经没有任何索引（v3.71.0 换到 member_id 了），
     * 写 {@code WHERE member_name = ?} 就是全表扫；建索引更不行 —— 关联键会就此悄悄退回
     * member_name，改名断链的问题原样复活。按账号找人先经 {@code MemberService} 换成会员号。
     */
    private String memberName;

    /**
     * 券模编码
     */
    private String couponCode;

    /**
     * 券类型
     */
    private String couponType;

    /**
     * 券名称
     */
    private String couponName;

    /**
     * 状态：0-未使用, 1-已使用, 2-已过期, 3-已作废
     */
    private CouponStatusEnum status;

    /**
     * 来源：DRAW, TASK, MANUAL_SEND
     */
    private String sourceType;

    /**
     * 关联单号
     */
    private String sourceBizId;

    /**
     * 有效期开始
     */
    private LocalDateTime validStartTime;

    /**
     * 有效期结束
     */
    private LocalDateTime validEndTime;

    /**
     * 核销时间
     */
    private LocalDateTime usedTime;

    // ------------------------------------------------------------------
    // 以下是 2026-09-15「券使用闭环」阶段 1 新增的列。
    //
    // 🔴 规则【快照】进来，而不是只存模板引用。
    //    券模板改了，用户手里那张不能跟着变 —— 运营把「满100减20」改成
    //    「满200减20」之后，如果核销读的是模板当前值，用户手里的券就贬值了。
    //    那不是显示问题，是资损与信任问题。
    //
    //    而且核销在支付路径上，快照换来零 join。与本项目「单据存快照」的
    //    既有约定一致（t_mall_order 存商品名、单价快照）。
    // ------------------------------------------------------------------

    /** 发券时的模板版本。排查用 —— 核销不读它，规则已经快照在下面几列里了 */
    private Integer templateVersion;

    /** 规则快照：FIXED-固定金额 / PERCENT-百分比 */
    private solvela.enums.CouponDiscountTypeEnum discountType;

    /** 规则快照：FIXED 时是抵扣额；PERCENT 时是折扣率（20 表示减 20%） */
    private java.math.BigDecimal discountValue;

    /** 规则快照：最低消费门槛，0 表示无门槛 */
    private java.math.BigDecimal minAmount;

    /** 规则快照：最高抵扣。PERCENT 时必填，不设上限就是资损口子 */
    private java.math.BigDecimal maxDiscount;

    /** 规则快照：CASH-抵现金 / SCORE-抵积分。🔴 两者不可比，试算时别跨类选最优 */
    private solvela.enums.CouponDeductTargetEnum deductTarget;

    /** 规则快照：适用范围 */
    private solvela.enums.CouponScopeTypeEnum scopeType;

    /** 规则快照：范围明细，json 数组 */
    private String scopeRefs;

    /**
     * 锁定它的单据号。
     *
     * <p>两个用途：兜底释放 job 靠它判断「这一笔还在不在」，
     * 以及<b>幂等</b> —— 同一笔订单重复调锁定，看到已经是自己就直接放行。
     */
    private String lockedBizId;

    /** 锁定时间。兜底 job 按它判超时 */
    private java.time.LocalDateTime lockedTime;

    /**
     * 本次实际抵扣额。核销时写、释放时清空。
     *
     * <p>⚠️ <b>权威在 {@code t_coupon_write_off}</b>，这里是冗余 ——
     * 为了券包「已使用」那个 tab 能零 join 显示「已抵扣 ¥20」，
     * 和 {@code t_member_notification.summary} 是同一个理由。
     * 两者不一致时以流水为准。
     */
    private java.math.BigDecimal discountAmount;

    /**
     * 创建人
     */
    private String createBy;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新人
     */
    private String updateBy;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;

}
