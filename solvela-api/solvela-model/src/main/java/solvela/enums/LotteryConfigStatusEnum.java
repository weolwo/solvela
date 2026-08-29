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
 * 上下线都是条件更新（{@code LotteryConfigDao.updateStatus(id, from, to)}），
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
}
