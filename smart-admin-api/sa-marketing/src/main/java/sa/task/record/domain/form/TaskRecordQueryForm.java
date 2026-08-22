package sa.task.record.domain.form;

import sa.base.common.domain.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 任务记录表 分页查询表单
 *
 * @Author weolwo
 * @Date 2026-04-18 21:02:56
 * @Copyright weolwo
 */

@Data
@EqualsAndHashCode(callSuper = false)
public class TaskRecordQueryForm extends PageParam {

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

    @Schema(description = "活动编码")
    private String activityCode;

    @Schema(description = "开始时间")
    private LocalDate validStartTimeBegin;

    @Schema(description = "开始时间")
    private LocalDate validStartTimeEnd;

    @Schema(description = "状态：0-进行中, 1-已完成, 2-已发奖, 3-已过期")
    private Integer status;

    @Schema(description = "达标时间")
    private LocalDate completeTimeBegin;

    @Schema(description = "达标时间")
    private LocalDate completeTimeEnd;

}
