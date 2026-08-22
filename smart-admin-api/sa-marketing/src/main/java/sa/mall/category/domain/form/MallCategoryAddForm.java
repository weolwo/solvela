package sa.mall.category.domain.form;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 商城-商品分类 新建表单
 *
 * @Author weolwo
 * @Date 2026-08-22 19:28:16
 * @Copyright weolwo
 */

@Data
public class MallCategoryAddForm {

    @Schema(description = "id", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "id 不能为空")
    private Long id;

    @Schema(description = "父级id：0-顶级分类。业务上限死两级", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "父级id：0-顶级分类。业务上限死两级 不能为空")
    private Long parentId;

    @Schema(description = "分类名称：如 数码3C / 虚拟权益", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "分类名称：如 数码3C / 虚拟权益 不能为空")
    private String categoryName;

    @Schema(description = "排序：从小到大", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "排序：从小到大 不能为空")
    private Integer sort;

    @Schema(description = "状态：0-禁用, 1-启用", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "状态：0-禁用, 1-启用 不能为空")
    private Integer status;

}