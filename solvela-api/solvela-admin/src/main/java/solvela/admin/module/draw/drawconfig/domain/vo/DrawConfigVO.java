package solvela.admin.module.draw.drawconfig.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import solvela.enums.DrawModeEnum;
import solvela.enums.EnableStatusEnum;

import java.time.LocalDateTime;

/**
 * 抽奖配置 VO
 */
@Data
public class DrawConfigVO {

    @Schema(description = "主键id")
    private Long id;

    @Schema(description = "活动编码")
    private String activityCode;

    @Schema(description = "活动名称")
    private String activityName;

    @Schema(description = "抽奖配置编码。脚本挂载点 DRAW_PLAY 用的就是它")
    private String drawCode;

    @Schema(description = "抽奖名称")
    private String drawName;

    @Schema(description = "抽奖算法")
    private DrawModeEnum drawMode;

    @Schema(description = "重置周期")
    private String resetPeriod;

    @Schema(description = "底下有几个奖池")
    private Integer poolCount;

    @Schema(description = "状态：0-关闭, 1-开启")
    private EnableStatusEnum status;

    @Schema(description = "创建人。工作台自动创建的记为 workbench，迁移来的记为 migration")
    private String createBy;

    @Schema(description = "更新时间")
    private LocalDateTime updateTime;
}
