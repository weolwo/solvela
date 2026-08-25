package solvela.mall.category.domain.form;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 批量新建里的一个分类节点。
 *
 * <p><b>{@code children} 只允许一层</b>，对应 DDL 的「业务上限死两级」。
 * 结构上它是可以无限嵌套的（自引用），但 Service 会拒绝第三层 ——
 * 这里不用类型去卡是因为：真做成两个类（父/子各一个）之后，
 * 「运营在一级分类下批量建二级」这个场景又得走另一套结构，反而更绕。
 *
 * @Date 2026-08-23
 */
@Data
public class MallCategoryBatchItemForm {

    @Schema(description = "分类名称", requiredMode = Schema.RequiredMode.REQUIRED)
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

    /**
     * 子分类。<b>这是「同时新建上下级」的落点</b> —— 子分类的 parentId 依赖父分类的自增 id，
     * 而父分类此刻还没入库，所以前端没法在表单里填那个 id。
     * 让它嵌在父节点里，由服务端在一个事务里先建父、拿到 id 再建子。
     */
    @Schema(description = "子分类：仅当本批挂在顶级下时允许，且只能一层")
    @Valid
    private List<MallCategoryBatchItemForm> children = new ArrayList<>();
}
