package sa.mall.category.domain.form;

import sa.base.common.domain.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 商城-商品分类 分页查询表单
 *
 * @Author weolwo
 * @Date 2026-08-22 19:28:16
 * @Copyright weolwo
 */

@Data
@EqualsAndHashCode(callSuper = false)
public class MallCategoryQueryForm extends PageParam {

    @Schema(description = "分类名称：如 数码3C / 虚拟权益")
    private String categoryName;

    @Schema(description = "状态：0-禁用, 1-启用")
    private Integer status;

    @Schema(description = "创建人")
    private String createBy;

}
