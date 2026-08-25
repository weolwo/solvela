package solvela.base.sonicexcel.meta;

import solvela.base.sonicexcel.converter.SonicConverter;

import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * 一列的全部元数据。解析一次、缓存在 {@link SheetMeta} 里，读写共用。
 *
 * @param title          表头文本
 * @param alias          导入时兼容的历史表头
 * @param order          绝对列序（从 0 起）
 * @param format         单元格格式，空串表示不指定
 * @param width          列宽（字符数），-1 表示按表头估算
 * @param forceText      强制文本写出
 * @param javaType       字段声明类型
 * @param fieldName      字段名（报错定位用）
 * @param element        字段本身（Field / RecordComponent），带参转换器靠它读配置注解
 * @param getter         LambdaMetafactory 生成的取值器，导出用
 * @param setter         LambdaMetafactory 生成的赋值器，POJO 导入用；record / 无 setter 时为 null
 * @param componentIndex record canonical 构造器的参数下标；POJO 为 -1
 * @param converter      已实例化的转换器，无转换器时为恒等实现
 * @Date 2026-08-08
 */
public record ColumnMeta(
        String title,
        List<String> alias,
        int order,
        String format,
        int width,
        boolean forceText,
        Class<?> javaType,
        String fieldName,
        java.lang.reflect.AnnotatedElement element,
        Function<Object, Object> getter,
        BiConsumer<Object, Object> setter,
        int componentIndex,
        SonicConverter<Object, Object> converter
) {

    public boolean hasFormat() {
        return format != null && !format.isEmpty();
    }
}
