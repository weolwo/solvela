package sa.stat.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
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
public class ParticipationByTypeVO {

    @Schema(description = "玩法类型：DRAW / TASK / LOTTERY")
    private String activityType;

    @Schema(description = "参与人数（该类型内按 member_name 去重）")
    private Integer memberCount;
}
