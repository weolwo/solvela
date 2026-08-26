package solvela.base.sonicexcel.annotation;

import solvela.enums.BaseEnum;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 声明这一列走枚举翻译，配合 {@code @SonicTitle(converter = SonicEnumConverter.class)} 使用。
 *
 * <pre>{@code
 * @SonicTitle(value = "商品状态", converter = SonicEnumConverter.class)
 * @SonicEnum(GoodsStatusEnum.class)
 * private Integer goodsStatus;
 * }</pre>
 *
 * <p>为什么枚举类型放在单独的注解上而不是 {@code @SonicTitle} 的属性里：
 * 转换器实例是<b>按类缓存的单例</b>，参数只能从字段的注解上读（见 SonicContext#element）。
 * 把它塞进 @SonicTitle 会让那个通用注解为了一种转换器长出专用属性。
 *
 * @Date 2026-08-08
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.RECORD_COMPONENT, ElementType.METHOD})
public @interface SonicEnum {

    Class<? extends BaseEnum> value();
}
