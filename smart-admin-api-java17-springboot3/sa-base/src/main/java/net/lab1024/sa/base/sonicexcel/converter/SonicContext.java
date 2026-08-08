package net.lab1024.sa.base.sonicexcel.converter;

import java.lang.reflect.AnnotatedElement;

/**
 * 转换器上下文：让转换器知道自己在处理哪一行哪一列，出错时能定位。
 *
 * @param rowIndex    Excel 物理行号（从 0 起，0 是表头）
 * @param columnIndex 列下标（从 0 起）
 * @param title       列标题
 * @param javaType    实体侧字段类型。枚举、数字这类转换器需要它才能知道往哪个类型转
 * @param element     字段本身（POJO 是 Field，record 是 RecordComponent）。
 *                    <b>带参转换器靠它读自己的配置注解</b>，比如 {@code @SonicDict("GOODS_PLACE")} ——
 *                    转换器实例是按类缓存的单例，参数只能从这里来。
 *                    注意它每个单元格都会被访问一次，读注解的结果请自行按 element 缓存
 * @param rowObject   导出时是整行的实体对象；导入时为 null（对象还没构造出来）
 * @Date 2026-08-08
 */
public record SonicContext(int rowIndex,
                           int columnIndex,
                           String title,
                           Class<?> javaType,
                           AnnotatedElement element,
                           Object rowObject) {
}
