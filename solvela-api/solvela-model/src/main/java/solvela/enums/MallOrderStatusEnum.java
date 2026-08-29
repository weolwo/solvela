package solvela.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 商城订单状态，对齐 {@code t_mall_order.status}。
 *
 * <h3>⚠️ 取值是跳跃的，不是连续序号</h3>
 * 0/10/20/… 之间留出空档，是为了将来插入中间态（比如「待发货」）时不用重排既有值。
 * <b>所以任何地方都别写 {@code status < 30} 这种范围判断，只能逐个比。</b>
 * 这条约束原先写在 {@code MallConst.ORDER_STATUS_*} 的注释里，随常量一起迁到这里。
 *
 * <p>🔴 订单模块尚未实现：{@code t_mall_order} 当前零行，这七个取值在改造时
 * <b>一处引用都没有</b>。枚举先建好，是为了模块开工时有唯一的一份声明可依。
 * 也因此本枚举<b>没有真实数据验证过</b>，只有编译期与 DDL 注释两重保证。
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
}
