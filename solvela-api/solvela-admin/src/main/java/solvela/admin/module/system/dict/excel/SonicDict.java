package solvela.admin.module.system.dict.excel;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 声明这一列走字典翻译，配合 {@code @SonicTitle(converter = SonicDictConverter.class)} 使用。
 *
 * <pre>{@code
 * @SonicTitle(value = "产地", converter = SonicDictConverter.class)
 * @SonicDict("GOODS_PLACE")
 * private String place;
 * }</pre>
 *
 * @Date 2026-08-08
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.RECORD_COMPONENT, ElementType.METHOD})
public @interface SonicDict {

    /**
     * 字典编码，如 {@code GOODS_PLACE}。
     */
    String value();

    /**
     * 多值分隔符。字段里存 {@code "1,2,3"} 时逐个翻译再拼回去。
     */
    String separator() default ",";
}
