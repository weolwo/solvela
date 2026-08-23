package sa.scriptengine.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 脚本引用关系 VO
 */
@Data
public class ScriptRefVO {

    @Schema(description = "id")
    private Long id;

    @Schema(description = "脚本编码")
    private String scriptCode;

    @Schema(description = "脚本名称")
    private String scriptName;

    @Schema(description = "挂载点枚举名，如 PRIZE_POOL_ENTRY")
    private String refPoint;

    @Schema(description = "挂载点中文名，如「奖池 - 准入判定」")
    private String refPointTitle;

    @Schema(description = "引用方类型")
    private String refType;

    @Schema(description = "引用方业务编码")
    private String refId;

    @Schema(description = "挂载槽位")
    private String refSlot;

    @Schema(description = "状态：0-停用, 1-启用")
    private Integer status;

    @Schema(description = "更新时间")
    private LocalDateTime updateTime;
}
