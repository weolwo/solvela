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
}
