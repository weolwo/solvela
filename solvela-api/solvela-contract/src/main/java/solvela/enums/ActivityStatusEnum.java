package solvela.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 活动状态，对齐 {@code t_activity_config.status}。
 *
 * <p>⚠️ 措辞是「上线 / 下线」而不是「启用 / 停用」，所以没有复用 {@link EnableStatusEnum}——
 * desc 要发给前端展示，两套说法混用会让页面上出现两种叫法。
 *
 * <h3>{@link #NOT_START} 不是「停用」</h3>
 * 新建的活动落在这一档，表示<b>还没配完、从未上过线</b>；{@link #OFFLINE} 表示<b>上过线又撤下来</b>。
 * 两者对运营是不同的事：前者要接着配，后者要查为什么撤。
 * 也因此 {@code updateStatus} 只接受 ONLINE / OFFLINE 两个目标值 ——
 * 活动一旦离开「未开始」就回不去，见 {@link #canTransitTo}。
 *
 * <p>上线还是结构锁的开关：{@code PrizePoolConfigService} 判到 {@link #ONLINE} 之后
 * 库存只增不减、禁止删奖项/删池/池内增删坑位。
 *
 * @Author alaric
 * @Date 2026-08-29
 */
@Getter
@AllArgsConstructor
public enum ActivityStatusEnum implements BaseEnum {

    /**
     * 未开始：新建默认落这一档，还没上过线
     */
    NOT_START(0, "未开始"),

    ONLINE(1, "上线"),

    /**
     * 下线：上过线又被撤下来，与 {@link #NOT_START} 不是一回事
     */
    OFFLINE(2, "下线"),
    ;

    private final Integer value;

    private final String desc;

    /**
     * 本状态能否迁到 {@code to}。未开始是唯一的初始状态，离开就回不去：
     * 回到未开始等于让上过线的活动又能被删掉（删除只认未开始），发奖流水的追溯链就断在源头。
     *
     * <p>未开始可以直接到下线：批量「禁用」里混着还没上过线的活动时照常处理（经确认保留的现状）。
     *
     * <p>🔴 闸门在 SQL 里：{@code ActivityConfigMapper.xml} 的 {@code markOnline} / {@code markOffline}
     * 带旧状态条件，删除也只认未开始。{@code ActivityConfigStatusSqlTest} 断言 SQL 与这里一一对应。
     *
     * <p>不写 default：新增一个状态时编译器会要求在这里表态。
     */
    public boolean canTransitTo(ActivityStatusEnum to) {
        return switch (this) {
            case NOT_START -> to == ONLINE || to == OFFLINE;
            case ONLINE -> to == OFFLINE;
            case OFFLINE -> to == ONLINE;
        };
    }
}
