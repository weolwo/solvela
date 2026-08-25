package solvela.scriptengine.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 可挂载点，前端下拉用
 */
@Data
public class ScriptRefPointVO {

    @Schema(description = "挂载点枚举名，如 PRIZE_POOL_ENTRY")
    private String refPoint;

    @Schema(description = "中文名，如「奖池 - 准入判定」")
    private String title;

    @Schema(description = "引用方类型")
    private String refType;

    @Schema(description = "挂载槽位")
    private String refSlot;

    @Schema(description = "本挂载点只接受这个场景的脚本")
    private String expectedScene;

    @Schema(description = "期望场景的中文名")
    private String expectedSceneTitle;
}
