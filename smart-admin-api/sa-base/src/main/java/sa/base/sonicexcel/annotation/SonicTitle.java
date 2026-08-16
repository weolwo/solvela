package sa.base.sonicexcel.annotation;

import sa.base.sonicexcel.converter.SonicConverter;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 列绑定注解，导出与导入共用一套映射。
 *
 * <p>只有标注了本注解的字段才是列，没标注的一律忽略（没有 {@code @ExcelIgnore} 这种反向开关）。
 *
 * @Date 2026-08-08
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
// RECORD_COMPONENT 不能省：只写 FIELD 的话，注解能否从 record 组件传播到合成字段是有条件的，
// 解析端就得绕道 getDeclaringRecord().getDeclaredField(name)。本框架把 record 当一等公民，一次写对。
@Target({ElementType.FIELD, ElementType.RECORD_COMPONENT, ElementType.METHOD})
public @interface SonicTitle {

    /**
     * Excel 表头名称。导出时写出，导入时用于匹配列。
     */
    String value();

    /**
     * 导入时兼容的历史表头名。
     *
     * <p>中文表头改字是日常，没有 alias 的话改一次表头，用户手里所有旧模板全部导入失败。
     */
    String[] alias() default {};

    /**
     * 绝对列序（从 0 起）。
     *
     * <p>规则是**要么全不写，要么全写且构成 0..n-1 的连续序列**，出现"写一半"或有空洞直接报错。
     * 不写时：record 按组件声明顺序，POJO 按字段声明顺序。
     */
    int index() default -1;

    /**
     * 单元格格式，如 {@code "yyyy-MM-dd HH:mm:ss"} / {@code "#,##0.00"}。
     */
    String format() default "";

    /**
     * 列宽（字符数）。-1 表示按表头文本估算（中文按 2 个字符计）。
     */
    int width() default -1;

    /**
     * 强制以文本写出。手机号、身份证、订单号这类"看起来是数字但不是数值"的列打开它。
     */
    boolean forceText() default false;

    /**
     * 双向转换器。默认恒等。
     */
    @SuppressWarnings("rawtypes")
    Class<? extends SonicConverter> converter() default SonicConverter.None.class;
}
