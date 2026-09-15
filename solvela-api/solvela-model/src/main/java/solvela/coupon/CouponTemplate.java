package solvela.coupon;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import solvela.enums.CouponDeductTargetEnum;
import solvela.enums.CouponDiscountTypeEnum;
import solvela.enums.CouponScopeTypeEnum;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 优惠券模板 实体类。规则住这里。
 *
 * <h3>🔴 按 (coupon_code, version) 不可变</h3>
 * 改规则 = 新增一个 version，永远不要原地改。
 *
 * <p>和通知模板是同一个道理，但<b>后果更重</b>：用户手里那张「满100减20」，
 * 运营把模板改成「满200减20」之后，如果核销时读的是模板当前值，
 * <b>用户手里的券就贬值了</b>。通知模板改版只是措辞变了，券改版是钱变了。
 *
 * <h3>所以核销根本不读这张表</h3>
 * 发券时把规则<b>快照</b>进 {@code t_member_coupon}，核销只读会员券行。
 * 模板留着版本是为了让运营能回答「这张券当时是什么规则」，
 * 以及发新券时有个地方配。
 *
 * <p>⚠️ 复合主键 {@code (coupon_code, version)}，实体上<b>没有也不该有</b>
 * {@code @TableId} —— MyBatis-Plus 的单主键注解表达不了复合主键。
 * 于是 {@code selectById} / {@code updateById} / {@code deleteById}
 * 对本实体<b>生成不出来</b>，调了会在运行期抛
 * {@code Invalid bound statement (not found)}，而编译期毫无迹象。
 * 按主键的操作一律在 Dao 里显式写。这个坑 2026-09-15 在通知模板上踩过一次。
 *
 * @Author alaric
 * @Date 2026-09-15
 * @Copyright weolwo
 */
@Data
@TableName("t_coupon_template")
public class CouponTemplate {

    /** 券模编码：跨环境稳定，发券时引用它 */
    private String couponCode;

    /** 版本号。🔴 改规则=新增版本，永不原地改 */
    private Integer version;

    private String couponName;

    /** FIXED-固定金额 / PERCENT-百分比 */
    private CouponDiscountTypeEnum discountType;

    /** FIXED 时是抵扣额；PERCENT 时是折扣率（20 表示减 20%） */
    private BigDecimal discountValue;

    /** 最低消费门槛，0 表示无门槛。订单金额低于它这张券用不了 */
    private BigDecimal minAmount;

    /**
     * 最高抵扣。
     *
     * <p>🔴 {@code PERCENT} 时<b>必填</b>：不设上限的「8 折」碰上一台 iPhone
     * 就是一次资损，而且不报错、只是少收钱。校验在 Service 层。
     */
    private BigDecimal maxDiscount;

    /**
     * 抵扣什么：CASH-现金 / SCORE-积分。
     *
     * <p>🔴 两者<b>不可比</b>，试算时不要跨类选「最优」—— 1 积分 ≠ 1 元，
     * 而汇率是业务定义、还会变。
     */
    private CouponDeductTargetEnum deductTarget;

    /** 适用范围 */
    private CouponScopeTypeEnum scopeType;

    /**
     * 范围明细，json 数组。{@code ALL} 时为空。
     *
     * <p>⚠️ 优先用 {@code CATEGORY} 而不是 {@code COMMODITY}：绑商品 id 的话，
     * 每上一个新商品都要回头改所有相关的券，而漏改不报错、只是那张券在新商品上
     * 莫名其妙用不了。
     */
    private String scopeRefs;

    /** 发券后 N 天过期。与 {@link #validEndTime} 二选一 */
    private Integer validDays;

    /** 固定失效时间（如活动结束）。与 {@link #validDays} 二选一 */
    private LocalDateTime validEndTime;

    private String remark;

    /** 1-启用 0-停用。🔴 只停用不删除：删了历史券就查不到当时的规则 */
    private Integer status;

    private String createBy;

    private LocalDateTime createTime;

    private String updateBy;

    private LocalDateTime updateTime;
}
