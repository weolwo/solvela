package solvela.base.common.excel;

import solvela.base.common.enumeration.BaseEnum;
import solvela.base.common.util.SolvelaEnumUtil;
import solvela.base.sonicexcel.SonicExcelException;
import solvela.base.sonicexcel.converter.SonicContext;
import solvela.base.sonicexcel.converter.SonicConverter;

import java.lang.reflect.AnnotatedElement;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 枚举 value ↔ desc 的双向翻译，把 {@code SolvelaEnumUtil.getEnumDescByValue(...)}
 * 这类样板从 service 里收走。
 *
 * <p>无依赖、无可变状态，不用注册成 Bean，框架会用无参构造实例化。
 *
 * @Date 2026-08-08
 */
public final class SonicEnumConverter implements SonicConverter<Object, String> {

    /**
     * 每个单元格都会走一次转换，注解不能每次都反射读 —— 按字段缓存。
     */
    private final Map<AnnotatedElement, Class<? extends BaseEnum>> enumTypes = new ConcurrentHashMap<>();

    @Override
    public String exportConvert(Object value, SonicContext ctx) {
        if (value == null) {
            return null;
        }
        String desc = SolvelaEnumUtil.getEnumDescByValue(value, enumType(ctx));
        if (desc == null) {
            throw new SonicExcelException("枚举值 " + value + " 在 "
                    + enumType(ctx).getSimpleName() + " 里没有对应项");
        }
        return desc;
    }

    @Override
    public Object importConvert(String value, SonicContext ctx) {
        if (value == null) {
            return null;
        }
        BaseEnum matched = SolvelaEnumUtil.getEnumByDesc(value, enumType(ctx));
        if (matched == null) {
            throw new SonicExcelException("「" + value + "」不是合法的取值");
        }
        return matched.getValue();
    }

    private Class<? extends BaseEnum> enumType(SonicContext ctx) {
        return enumTypes.computeIfAbsent(ctx.element(), element -> {
            SonicEnum annotation = element.getAnnotation(SonicEnum.class);
            if (annotation == null) {
                throw new SonicExcelException("列「" + ctx.title() + "」用了 SonicEnumConverter，"
                        + "但字段上没有 @SonicEnum 指定枚举类");
            }
            return annotation.value();
        });
    }
}
