package solvela.base.sonicexcel.option;

import solvela.base.sonicexcel.annotation.SonicEnum;

import solvela.enums.BaseEnum;
import solvela.base.sonicexcel.SonicExcelException;
import solvela.base.sonicexcel.converter.SonicContext;
import solvela.base.sonicexcel.option.SonicOptionProvider;

import java.util.Arrays;
import java.util.List;

/**
 * 用 {@code @SonicEnum} 指定的枚举，给导入模板生成下拉选项（取各枚举项的 desc）。
 *
 * <pre>{@code
 * @SonicTitle("商品状态")
 * @SonicEnum(GoodsStatusEnum.class)
 * @SonicOptions(provider = SonicEnumOptionProvider.class)
 * private String goodsStatus;
 * }</pre>
 *
 * <p>复用 {@code @SonicEnum} 而不是让用户把选项字面量抄一遍：枚举加一项，模板下拉自动跟着变，
 * 不会出现"代码改了模板没改"的漂移。
 *
 * @Date 2026-08-08
 */
public final class SonicEnumOptionProvider implements SonicOptionProvider {

    @Override
    public List<String> options(SonicContext ctx) {
        SonicEnum annotation = ctx.element().getAnnotation(SonicEnum.class);
        if (annotation == null) {
            throw new SonicExcelException("列「" + ctx.title() + "」用了 SonicEnumOptionProvider，"
                    + "但字段上没有 @SonicEnum 指定枚举类");
        }
        return Arrays.stream(annotation.value().getEnumConstants())
                .map(BaseEnum::getDesc)
                .toList();
    }
}
