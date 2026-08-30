package solvela.admin.module.risk.promotiongroup.domain.form;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import solvela.base.domain.PageParam;
import solvela.enums.EnableStatusEnum;

/**
 * 优惠配置分组 分页查询表单
 *
 * @Author alaric
 * @Date 2026-08-30
 */
@Data
@EqualsAndHashCode(callSuper = false)
public class PromotionGroupQueryForm extends PageParam {

    @Schema(description = "分组名称，模糊匹配")
    private String groupName;

    @Schema(description = "分组编码")
    private String groupCode;

    @Schema(description = "状态：0-停用, 1-启用")
    private EnableStatusEnum status;
}
