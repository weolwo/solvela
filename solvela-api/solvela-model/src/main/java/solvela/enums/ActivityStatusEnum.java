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
 * 活动一旦上过线就回不到「未开始」。
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
}
