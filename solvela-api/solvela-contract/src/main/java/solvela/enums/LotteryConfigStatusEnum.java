package solvela.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 彩票玩法状态，对齐 {@code t_lottery_config.status}。
 *
 * <p>⚠️ 措辞是「上线 / 下线」，没有复用 {@link EnableStatusEnum}（停用/启用）——
 * desc 要发给前端展示，同一个页面上出现两种叫法是运营看得见的毛病。
 * 与 {@link ActivityStatusEnum} 用词一致，但那边多一个「未开始」，两者不能互换。
 *
 * <p>{@link #ONLINE} 是运行态的放行闸门：{@code TicketIssueService} 判到非上线直接拒绝发号。
 * 上下线都是条件更新（{@code LotteryConfigDao.markOnline / markOffline}），
 * 抢不到说明状态已被别人改过。
 *
 * @Author alaric
 * @Date 2026-08-29
 */
@Getter
@AllArgsConstructor
public enum LotteryConfigStatusEnum implements BaseEnum {

    OFFLINE(0, "下线"),

    ONLINE(1, "上线"),
    ;

    private final Integer value;

    private final String desc;

    /**
     * 本状态能否迁到 {@code to}：上线、下线互相切换，新建一律下线。
     *
     * <p>🔴 闸门在 SQL 里：{@code LotteryConfigMapper.xml} 的 {@code markOnline} / {@code markOffline}
     * 带旧状态条件，两个运营同时点只有一个生效。{@code LotteryConfigStatusSqlTest} 断言一一对应。
     *
     * <p>不写 default：新增一个状态时编译器会要求在这里表态。
     */
    public boolean canTransitTo(LotteryConfigStatusEnum to) {
        return switch (this) {
            case OFFLINE -> to == ONLINE;
            case ONLINE -> to == OFFLINE;
        };
    }
}
