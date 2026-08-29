package solvela.mall.category.domain.command;

import solvela.enums.EnableStatusEnum;
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
public class MallCategoryBatchItemCommand {

    /** 分类名称 */
    private String categoryName;

    /** 分类图标 file_id（C端宫格导航用） */
    private Long iconFileId;

    /** 排序：从小到大 */
    private Integer sort;

    /** 状态：0-禁用, 1-启用 */
    private EnableStatusEnum status;

    /**
     * 子分类。<b>这是「同时新建上下级」的落点</b> —— 子分类的 parentId 依赖父分类的自增 id，
     * 而父分类此刻还没入库，所以前端没法在表单里填那个 id。
     * 让它嵌在父节点里，由服务端在一个事务里先建父、拿到 id 再建子。
     */
    private List<MallCategoryBatchItemCommand> children = new ArrayList<>();
}
