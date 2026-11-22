package solvela.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 商城订单状态，对齐 {@code t_mall_order.status}。
 *
 * <h3>⚠️ 取值是跳跃的，不是连续序号</h3>
 * 0/10/20/… 之间留出空档，是为了将来插入中间态（比如「待发货」）时不用重排既有值。
 * <b>所以任何地方都别写 {@code status < 30} 这种范围判断，只能逐个比。</b>
 *
 * <h3>🔴 状态机的规则在这里，闸门在 SQL 里</h3>
 * {@link #canTransitTo} 是唯一一份「谁能变成谁」的声明；真正执行迁移的是
 * {@code MallOrderDao} 里带旧状态条件的 UPDATE —— 并发下只有它挡得住两个线程同时改同一行，
 * 所以<b>不要</b>改成「先读出实体、在内存里判断、再写回」。
 * 那几条 SQL 里的状态值引用本枚举，不写数字；
 * {@code MallOrderStatusSqlTest} 渲染出真正发给数据库的 SQL，
 * 断言 DAO 的迁移与 {@link #canTransitTo} 一条不多、一条不少。
 * 这套做法的约定与推广注意事项见 {@code docs/架构/领域实体轻充血-架构设计文档.md}。
 *
 * @Author alaric
 * @Date 2026-08-29
 */
@Getter
@AllArgsConstructor
public enum MallOrderStatusEnum implements BaseEnum {

    UNPAID(0, "待支付"),

    PENDING(10, "待履约"),

    FULFILLING(20, "履约中"),

    FINISHED(30, "已完成"),

    CANCELLED(40, "已取消"),

    REFUNDED(50, "已退款"),

    FAILED(60, "履约失败"),
    ;

    private final Integer value;

    private final String desc;

    /**
     * 本状态能否迁到 {@code to}。
     *
     * <p>⚠️ 已退款(50) 至今没有任何入边，履约失败(60) 也没有出边 ——
     * 退款与「补配后重发」两条路都还没实现。实现时先在这里加边，
     * {@code MallOrderStatusSqlTest} 会逼着 DAO 里补上对应的闸门 SQL。
     *
     * <p>不写 default：新增一个状态时编译器会要求在这里表态。
     */
    public boolean canTransitTo(MallOrderStatusEnum to) {
        return switch (this) {
            case UNPAID -> to == PENDING || to == CANCELLED;
            case PENDING -> to == FULFILLING;
            case FULFILLING -> to == FINISHED || to == FAILED;
            case FINISHED, CANCELLED, REFUNDED, FAILED -> false;
        };
    }

    /**
     * 这一单的资产是否已经结清（扣过款）。待支付与已取消不算，其余都算 ——
     * 已退款也算：它确实扣过，退款是另一笔账。
     *
     * <p>🔴 它和 {@code MallOrderMapper.xml} 的 {@code paid_statuses} 片段是同一条口径：
     * 打点（任务进度）、订单统计、用券确认都按它判。两边对不上时，
     * 表现是「发货了但任务进度不涨」或统计金额对不上，不报错 ——
     * {@code MallOrderStatusSqlTest} 守着两边一致。
     */
    public boolean isSettled() {
        return this != UNPAID && this != CANCELLED;
    }
}
