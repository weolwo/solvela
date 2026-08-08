package net.lab1024.sa.base.sonicexcel.meta;

import java.util.List;

/**
 * 一个实体类型的整表元数据。<b>读写完全对称</b> —— 同一份 SheetMeta 既驱动导出也驱动导入，
 * 这是"下载的模板就是能上传的模板"能成立的前提。
 *
 * @param columns 已按 order 排好序，不可变
 * @Date 2026-08-08
 */
public record SheetMeta(
        Class<?> type,
        List<ColumnMeta> columns,
        RowConstructor constructor
) {

    public int columnCount() {
        return columns.size();
    }
}
