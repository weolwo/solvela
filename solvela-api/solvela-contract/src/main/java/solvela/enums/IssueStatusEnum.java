package solvela.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;
import solvela.enums.BaseEnum;

/**
 * 期号状态，对齐 t_lottery_issue.status
 * <p>
 * 与 {@link TicketStatusEnum} 一样实现 BaseEnum 并把字段命名为 value，
 * 否则一旦被 @CheckEnum 引用就会重蹈「白名单全 null」的覆辙。
 *
 * @Author weolwo
 * @Date 2026-05-09
 */
@Getter
@AllArgsConstructor
public enum IssueStatusEnum implements BaseEnum {

    /**
     * 待开奖：可售卖、可编辑时间、可删除（未发过号时）
     */
    WAIT(0, "待开奖"),

    /**
     * 核销中：开奖号码已定案且不可再变，正在分批比对号码。
     * 这一档是可恢复的断点标记 —— 中断后重跑会接着比对，不会重复发奖。
     * DDL 里原始注释写的是「部分开奖」，那是预留语义，本系统一期只开一次奖。
     */
    STAGED(1, "核销中"),

    /**
     * 已开奖：全部号码比对完毕，中奖记录已确定
     */
    OPENED(2, "已开奖"),
    ;

    private final Integer value;

    private final String desc;

    /**
     * 本状态能否迁到 {@code to}：待开奖 → 核销中 → 已开奖，单向，一期只开一次奖。
     *
     * <p>🔴 闸门在 SQL 里：{@code LotteryIssueMapper.xml} 的 {@code startSettle}（同时定案开奖号码）与
     * {@code finishSettle} 都带旧状态条件。发号的 {@code increaseSoldCount} 与删除也只认待开奖。
     * {@code LotteryIssueStatusSqlTest} 断言那些 SQL 与这里一一对应。
     *
     * <p>不写 default：新增一个状态时编译器会要求在这里表态。
     */
    public boolean canTransitTo(IssueStatusEnum to) {
        return switch (this) {
            case WAIT -> to == STAGED;
            case STAGED -> to == OPENED;
            case OPENED -> false;
        };
    }
}
