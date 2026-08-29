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
