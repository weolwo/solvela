package sa.mall.category.domain.form;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 商城-商品分类 保存表单（id 为空即新建）。
 *
 * <p>合并了生成器留下的 AddForm / UpdateForm：那两个一个强制要求传 id（新建时根本没有），
 * 一个只有 id 一个字段（什么都改不了），都不能用。
 *
 * @Date 2026-08-23
 */
@Data
public class MallCategorySaveForm {

    @Schema(description = "分类id：新建传 null")
    private Long id;

    /**
     * 0 = 顶级分类。<b>业务上限死两级</b> —— parent 的 parent 必须为 0，在 Service 里卡。
     * 不卡的话运营能建出五级菜单，而 C 端宫格导航只渲染两层，第三层往下直接消失。
     */
    @Schema(description = "父级id：0-顶级分类，业务上限死两级")
    private Long parentId;

    @Schema(description = "分类名称：如 数码3C / 虚拟权益", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "分类名称不能为空")
    @Size(max = 50, message = "分类名称最长 50 字")
    private String categoryName;

    @Schema(description = "分类图标 file_id（C端宫格导航用）")
    private Long iconFileId;

    @Schema(description = "排序：从小到大")
    @Min(value = 0, message = "排序不能为负")
    private Integer sort;

    @Schema(description = "状态：0-禁用, 1-启用")
    private Integer status;
}
