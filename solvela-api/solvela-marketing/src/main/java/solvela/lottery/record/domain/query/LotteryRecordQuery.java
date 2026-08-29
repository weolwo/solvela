package solvela.lottery.record.domain.query;

import lombok.Data;
import lombok.EqualsAndHashCode;
import solvela.base.domain.PageParam;
import solvela.base.validation.enumeration.CheckEnum;
import solvela.enums.TicketStatusEnum;

import java.time.LocalDate;

/**
 * 彩票投注记录分页查询的<b>领域参数</b>。Form 跟着某个端的页面走，Query 跟着领域能力走。
 *
 * <p>分层与取舍的完整说明见 {@code MemberWalletQuery}。这里刻意没有 {@code @Schema}
 * 与校验注解 —— 接口文档和参数校验是端的职责。
 */

@Data
@EqualsAndHashCode(callSuper = false)
public class LotteryRecordQuery extends PageParam {

    /** 彩票编码 */
    private String lotteryCode;

    /** 归属期号 */
    private String issueNo;

    /** 彩票号码 */
    private String ticketNumber;

    /** 创建时间 */
    private LocalDate createTimeBegin;

    /** 创建时间 */
    private LocalDate createTimeEnd;

    @CheckEnum(value = TicketStatusEnum.class, message = "中奖状态: 0-未开奖, 1-未中奖, 已开奖 错误")
    private TicketStatusEnum winStatus;

    /**
     * 会员号（精确匹配，走 member_id 索引）。
     *
     * <p>🔴 这里<b>刻意不再收账号</b>：v3.71.0 之后 {@code member_name} 只是展示快照、
     * 身上没有任何索引，拿它当查询条件必是全表扫；而它又是可改的，
     * 用户改名之后按旧名字查等于查不到 —— 「不报错，只是查不到了」正是这次换键要消灭的。
     * 后台要按账号找人，先经 {@code MemberService.getMemberId} 换成会员号。
     */
    private Long memberId;

    /** 奖励等级 */
    private Integer prizeLevel;

}
