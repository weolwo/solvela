package solvela.admin.module.system.job.api.domain;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import solvela.base.common.domain.PageParam;
import solvela.base.common.swagger.SchemaEnum;
import solvela.base.common.validator.enumeration.CheckEnum;
import solvela.admin.module.system.job.constant.SolvelaJobTriggerTypeEnum;
import org.hibernate.validator.constraints.Length;

/**
 * 定时任务 分页查询
 *
 * @author huke
 * @date 2024/6/17 20:50
 */
@Data
public class SolvelaJobQueryForm extends PageParam {

    @Schema(description = "搜索词|可选")
    @Length(max = 50, message = "搜索词最多50字符")
    private String searchWord;

    @SchemaEnum(desc = "触发类型", value = SolvelaJobTriggerTypeEnum.class)
    @CheckEnum(value = SolvelaJobTriggerTypeEnum.class, message = "触发类型错误")
    private String triggerType;

    @Schema(description = "是否启用|可选")
    private Boolean enabledFlag;

    @Schema(description = "是否删除|可选")
    private Boolean deletedFlag;

    @Schema(description = "分组|可选")
    private String jobGroup;

    /**
     * 是否包含已终结的一次性任务。
     *
     * <p>默认不含：活动多了以后终态任务只增不减，不过滤的话列表会被历史任务淹没
     */
    @Schema(description = "是否包含已终结的一次性任务|默认否")
    private Boolean includeTerminal;
}
