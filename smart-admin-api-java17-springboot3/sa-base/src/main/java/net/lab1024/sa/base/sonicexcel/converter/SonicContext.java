package net.lab1024.sa.base.sonicexcel.converter;

/**
 * 转换器上下文：让转换器知道自己在处理哪一行哪一列，出错时能定位。
 *
 * @param rowIndex    Excel 物理行号（从 0 起，0 是表头）
 * @param columnIndex 列下标（从 0 起）
 * @param title       列标题
 * @param rowObject   导出时是整行的实体对象；导入时为 null（对象还没构造出来）
 * @Date 2026-08-08
 */
public record SonicContext(int rowIndex, int columnIndex, String title, Object rowObject) {
}
