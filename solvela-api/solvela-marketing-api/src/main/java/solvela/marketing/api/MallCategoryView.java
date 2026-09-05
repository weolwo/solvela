package solvela.marketing.api;

/**
 * 商品分类。
 *
 * <p>⚠️ <b>没有 categoryCode</b>，只有自增 id —— DDL 里解释过：分类是纯运营数据，
 * 代码不引用它（「一律按 code 引用」那条教训针对的是被代码硬编码引用的分类）。
 * 业务上限死两级，{@code parentId=0} 为顶级。
 *
 * @param iconUrl 分类图标的可直接访问 URL。
 *
 * <h3>🔴 下发 URL 而不是 file_id</h3>
 * 原先给的是 id，注释写着「理由同活动主图」。但那条理由（URL 带签名、有有效期）
 * 只在<b>对象存储 + 预签名</b>下成立 —— 本地存储下 URL 是
 * {@code publicUrlPrefix + storageKey}，storageKey 不可变，URL 就是永久的。
 *
 * <p>而给 id 的代价是实打实的：C 端没有「按 file_id 换 URL」的接口
 *（那个在 admin 上，要登录态），于是前端拿到 id 什么也做不了 ——
 * 商城的图因此一张都没显示过。真上了预签名 URL 那天，
 * 该做的是在这里签，不是把 id 甩给端上让它自己想办法。
 */
public record MallCategoryView(
        Long id,
        Long parentId,
        String categoryName,
        String iconUrl,
        Integer sort) {
}
