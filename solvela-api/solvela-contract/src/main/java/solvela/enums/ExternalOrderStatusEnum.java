package solvela.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.Getter;

/**
 * 外部场景消费单状态。<b>数值就是契约</b>，改取值要连 SQL 里那几条硬编码一起改。
 *
 * <p>状态机与商城订单同构，刻意取一样的数字 —— 两张单据在运营眼里是同一类东西，
 * 数字不一样只会让人每次都去查一遍对照表：
 *
 * <pre>
 *   0-待支付 → 10-待执行 → 20-执行中 → 30-成功 / 60-失败
 *            ↘ 40-已取消（超时或用户取消，券要放回去）
 * </pre>
 *
 * @Author alaric
 * @Date 2026-09-15
 */
@Getter
public enum ExternalOrderStatusEnum {

    /** 待支付。券已经<b>锁定</b>但还没确认 —— 这一段就是三阶段核销存在的理由 */
    UNPAID(0, "待支付"),

    /** 已支付，等着调外部接口。券在这一刻已经<b>确认</b>掉了 */
    PENDING(10, "待充值"),

    /** 正在调外部接口。这个状态本身就是幂等闸：抢到它的那一个才去调 */
    EXECUTING(20, "充值中"),

    /** 外部返回成功 */
    SUCCESS(30, "充值成功"),

    /**
     * 超时未支付 / 用户取消。券<b>放回未使用</b>。
     *
     * <p>⚠️ 只有 0-待支付 能走到这里。已经付过的单不走取消，走失败 ——
     * 那是两件事：一个是「没买」，一个是「买了但没给到」。
     */
    CANCELLED(40, "已取消"),

    /**
     * 外部返回失败。
     *
     * <p>🔴 <b>失败不放券</b>，和商城履约失败同一条规矩：东西还欠着用户，不是没买。
     * 放了券却没退钱，用户会拿到一个自相矛盾的结果。
     */
    FAILED(60, "充值失败"),
    ;

    @EnumValue
    private final Integer value;

    private final String desc;

    ExternalOrderStatusEnum(Integer value, String desc) {
        this.value = value;
        this.desc = desc;
    }
}
