package solvela.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.Getter;

/**
 * 外部场景消费单状态。<b>数值就是契约</b>：库里存的、C 端视图里给出去的都是它。
 *
 * <p>状态机与商城订单同构，刻意取一样的数字 —— 两张单据在运营眼里是同一类东西，
 * 数字不一样只会让人每次都去查一遍对照表：
 *
 * <pre>
 *   0-待支付 → 10-待执行 → 20-执行中 → 30-成功 / 60-失败
 *            ↘ 40-已取消（超时或用户取消，券要放回去）
 * </pre>
 *
 * <h3>🔴 状态机的规则在这里，闸门在 SQL 里</h3>
 * {@link #canTransitTo} 是唯一一份「谁能变成谁」的声明；真正执行迁移的是
 * {@code ExternalOrderDao} 里带旧状态条件的 UPDATE，不要改成先读出实体、在内存里判断再写回。
 * 那几条 SQL 里的状态值引用本枚举，不写数字；{@code ExternalOrderStatusSqlTest}
 * 渲染出真正发给数据库的 SQL，断言 DAO 的迁移与 {@link #canTransitTo} 一条不多、一条不少。
 * 约定见 {@code docs/架构/领域实体轻充血-架构设计文档.md}。
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

    /**
     * 本状态能否迁到 {@code to}。成功、取消、失败都是终态 ——
     * 失败(60) 之后的退款或重试还没实现，实现时先在这里加边，
     * {@code ExternalOrderStatusSqlTest} 会逼着 DAO 补上对应的闸门 SQL。
     *
     * <p>不写 default：新增一个状态时编译器会要求在这里表态。
     */
    public boolean canTransitTo(ExternalOrderStatusEnum to) {
        return switch (this) {
            case UNPAID -> to == PENDING || to == CANCELLED;
            case PENDING -> to == EXECUTING;
            case EXECUTING -> to == SUCCESS || to == FAILED;
            case SUCCESS, CANCELLED, FAILED -> false;
        };
    }
}
