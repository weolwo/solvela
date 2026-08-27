package solvela.mall.category.domain.command;

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
public class MallCategorySaveCommand {

    /** 分类id：新建传 null */
    private Long id;

    /**
     * 0 = 顶级分类。<b>业务上限死两级</b> —— parent 的 parent 必须为 0，在 Service 里卡。
     * 不卡的话运营能建出五级菜单，而 C 端宫格导航只渲染两层，第三层往下直接消失。
     */
    private Long parentId;

    /** 分类名称：如 数码3C / 虚拟权益 */
    private String categoryName;

    /** 分类图标 file_id（C端宫格导航用） */
    private Long iconFileId;

    /** 排序：从小到大 */
    private Integer sort;

    /** 状态：0-禁用, 1-启用 */
    private Integer status;
}
