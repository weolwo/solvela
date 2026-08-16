package sa.admin.module.business.goods.excel;

import jakarta.annotation.Resource;
import sa.admin.module.business.category.service.CategoryQueryService;
import sa.base.sonicexcel.SonicExcelException;
import sa.base.sonicexcel.converter.SonicContext;
import sa.base.sonicexcel.converter.SonicConverter;
import org.springframework.stereotype.Component;

/**
 * 商品类目 id → 类目名称。
 *
 * <p>转换器与它包装的组件同模块 —— 这条依赖 sa-admin 的 CategoryQueryService，
 * 所以放在 sa-admin，不进 sonicexcel 框架目录（框架要保持可整体搬走）。
 *
 * @Date 2026-08-08
 */
@Component
public class GoodsCategoryConverter implements SonicConverter<Long, String> {

    @Resource
    private CategoryQueryService categoryQueryService;

    @Override
    public String exportConvert(Long categoryId, SonicContext ctx) {
        return categoryId == null ? null : categoryQueryService.queryCategoryName(categoryId);
    }

    @Override
    public Long importConvert(String value, SonicContext ctx) {
        // 类目名不唯一、也没有反查入口，导入模板请直接给 categoryId
        throw new SonicExcelException("列「" + ctx.title() + "」只支持导出");
    }
}
