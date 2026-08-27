package solvela.task.record.domain.query;

import solvela.base.domain.PageParam;
import java.time.LocalDate;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 任务记录分页查询的<b>领域参数</b>。Form 跟着某个端的页面走，Query 跟着领域能力走。
 *
 * <p>分层与取舍的完整说明见 {@code MemberWalletQuery}。这里刻意没有 {@code @Schema}
 * 与校验注解 —— 接口文档和参数校验是端的职责。
 */

@Data
@EqualsAndHashCode(callSuper = false)
public class TaskRecordQuery extends PageParam {

    /**
     * 会员号（精确匹配，走 member_id 索引）。
     *
     * <p>🔴 这里<b>刻意不再收账号</b>：v3.71.0 之后 {@code member_name} 只是展示快照、
     * 身上没有任何索引，拿它当查询条件必是全表扫；而它又是可改的，
     * 用户改名之后按旧名字查等于查不到 —— 「不报错，只是查不到了」正是这次换键要消灭的。
     * 后台要按账号找人，先经 {@code MemberService.getMemberId} 换成会员号。
     */
    private Long memberId;

    /** 活动编码 */
    private String activityCode;

    /** 开始时间 */
    private LocalDate validStartTimeBegin;

    /** 开始时间 */
    private LocalDate validStartTimeEnd;

    /** 状态：0-进行中, 1-已完成, 2-已发奖, 3-已过期 */
    private Integer status;

    /** 达标时间 */
    private LocalDate completeTimeBegin;

    /** 达标时间 */
    private LocalDate completeTimeEnd;

}
