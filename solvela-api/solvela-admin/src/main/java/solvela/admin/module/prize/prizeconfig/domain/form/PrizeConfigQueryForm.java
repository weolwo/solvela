package solvela.admin.module.prize.prizeconfig.domain.form;

import solvela.base.domain.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 奖品配置表 分页查询表单
 *
 * @Author weolwo
 * @Date 2026-04-18 20:20:44
 * @Copyright weolwo
 */

@Data
@EqualsAndHashCode(callSuper = false)
public class PrizeConfigQueryForm extends PageParam {

    @Schema(description = "活动编码")
    private String activityCode;

    @Schema(description = "奖品编码")
    private String prizeCode;

    @Schema(description = "奖品级别")
    private Integer prizeLevel;

    @Schema(description = "奖品名称")
    private String prizeName;

    @Schema(description = "审批模式：0-自动免审, 1-人工审批")
    private Integer approveMode;

    @Schema(description = "状态：0-停用, 1-启用")
    private Integer status;

}
