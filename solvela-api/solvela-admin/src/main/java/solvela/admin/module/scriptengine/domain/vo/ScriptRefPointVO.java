package solvela.admin.module.scriptengine.domain.vo;

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

    @Schema(description = "挂载对象的中文名，如「抽奖配置」。挂载表单的字段标签用它")
    private String ownerTitle;

    @Schema(description = "是不是按 key 分组的多值槽位。true 时挂载必须传 refKey")
    private Boolean keyed;

    @Schema(description = "引擎里是否真的有代码会执行这个槽位。false 表示挂上去也不会生效")
    private Boolean wired;

    @Schema(description = "分组键的中文名，如「事件编码」。单值槽位为 null")
    private String keyTitle;

    @Schema(description = "本挂载点只接受这个场景的脚本")
    private String expectedScene;

    @Schema(description = "期望场景的中文名")
    private String expectedSceneTitle;
}
