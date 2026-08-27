package solvela.stat.domain.dto;

import lombok.Data;

/**
 * 各玩法类型参与人数
 *
 * <p>⚠️ 刻意不叫「占比」：同一个人既玩抽奖又做任务时，各类型各自去重后相加
 * 会把他算两次，各项之和大于实际总人数。前端用柱状图而不是饼图，不承诺占比语义。
 *
 * @Author weolwo
 * @Date 2026-08-02
 */
@Data
public class ParticipationByTypeDTO {

    /** 玩法类型：DRAW / TASK / LOTTERY */
    private String activityType;

    /** 参与人数（该类型内按 member_id 去重） */
    private Integer memberCount;
}
