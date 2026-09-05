package solvela.marketing.api;

/**
 * 商品分类。
 *
 * <p>⚠️ <b>没有 categoryCode</b>，只有自增 id —— DDL 里解释过：分类是纯运营数据，
 * 代码不引用它（「一律按 code 引用」那条教训针对的是被代码硬编码引用的分类）。
 * 业务上限死两级，{@code parentId=0} 为顶级。
 *
 * @param iconFileId 分类图标 file_id。<b>给的是 id 不是 URL</b>，理由同活动主图
 */
public record MallCategoryView(
        Long id,
        Long parentId,
        String categoryName,
        Long iconFileId,
        Integer sort) {
}
