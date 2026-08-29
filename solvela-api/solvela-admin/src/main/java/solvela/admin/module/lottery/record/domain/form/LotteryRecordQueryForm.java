package solvela.admin.module.lottery.record.domain.form;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import solvela.base.domain.PageParam;
import solvela.web.swagger.SchemaEnum;
import solvela.base.validation.enumeration.CheckEnum;
import solvela.enums.TicketStatusEnum;

import java.time.LocalDate;

/**
 * 用户号码记录 分页查询表单
 *
 * @Author weolwo
 * @Date 2026-04-19 11:57:08
 * @Copyright weolwo
 */

@Data
@EqualsAndHashCode(callSuper = false)
public class LotteryRecordQueryForm extends PageParam {

    @Schema(description = "彩票编码")
    private String lotteryCode;

    @Schema(description = "归属期号")
    private String issueNo;

    @Schema(description = "彩票号码")
    private String ticketNumber;

    @Schema(description = "创建时间")
    private LocalDate createTimeBegin;

    @Schema(description = "创建时间")
    private LocalDate createTimeEnd;

    @SchemaEnum(value = TicketStatusEnum.class, desc = "中奖状态: 0-未开奖, 1-未中奖, 已开奖")
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
    @Schema(description = "会员号")
    private Long memberId;

    @Schema(description = "奖励等级")
    private Integer prizeLevel;

}
