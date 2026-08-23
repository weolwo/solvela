package sa.mall.category.domain.form;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 分类批量新建：一次提交多个，可带一层子分类。
 *
 * <p><b>为什么要有这个接口</b>：分类结构基本是「建一次就不怎么动」的东西，
 * 而那一次往往就是要一口气把整棵树搭出来。逐个保存的话，建一个 6 分类的商城要点 6 次，
 * 中途还得为每个子分类重新选一遍上级。
 *
 * <p><b>为什么子分类嵌在父节点里而不是平铺 + 填 parentId</b>：
 * 子分类的 parentId 是父分类的自增 id，父分类此刻还没入库 —— 前端根本填不出这个值。
 * 嵌套结构让「先建父、拿 id、再建子」这件事留在服务端的同一个事务里。
 *
 * @Date 2026-08-23
 */
@Data
public class MallCategoryBatchSaveForm {

    /**
     * 本批统一挂在哪个上级下。0 = 顶级。
     *
     * <p>选了某个一级分类时，{@code categoryList} 里<b>不允许再有 children</b> ——
     * 那已经是第二级，再挂一层就是三级了。
     */
    @Schema(description = "上级分类id：0-顶级")
    private Long parentId;

    @Schema(description = "要新建的分类，可带一层子分类", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotEmpty(message = "至少要填一个分类")
    @Valid
    private List<MallCategoryBatchItemForm> categoryList = new ArrayList<>();
}
