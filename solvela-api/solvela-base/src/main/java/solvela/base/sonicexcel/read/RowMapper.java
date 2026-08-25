package solvela.base.sonicexcel.read;

import solvela.base.sonicexcel.SonicExcelException;
import solvela.base.sonicexcel.converter.SonicContext;
import solvela.base.sonicexcel.converter.SonicConverter;
import solvela.base.sonicexcel.meta.ColumnMeta;
import solvela.base.sonicexcel.meta.RowConstructor;
import solvela.base.sonicexcel.meta.SheetMeta;
import org.dhatim.fastexcel.reader.Cell;
import org.dhatim.fastexcel.reader.Row;

import java.util.List;

/**
 * 一行 → 一个对象。POJO 走 setter 注入，record 走 canonical 构造器。
 *
 * @Date 2026-08-08
 */
final class RowMapper<T> {

    private final SheetMeta meta;
    private final int[] positions;

    RowMapper(SheetMeta meta, int[] positions) {
        this.meta = meta;
        this.positions = positions;
        checkWritable();
    }

    /**
     * 结构性问题（POJO 少 setter、类没有无参构造）在开读之前就报，
     * 不要等读到第 5000 行才每行抛一次一模一样的异常。
     */
    private void checkWritable() {
        if (meta.constructor() instanceof RowConstructor.Unavailable(String reason)) {
            throw new SonicExcelException("无法用于导入：" + reason);
        }
        if (meta.type().isRecord()) {
            return;
        }
        for (int i = 0; i < meta.columns().size(); i++) {
            ColumnMeta col = meta.columns().get(i);
            if (positions[i] >= 0 && col.setter() == null) {
                throw new SonicExcelException(meta.type().getName() + "#" + col.fieldName()
                        + " 没有 setter，无法导入。请补 setter 或去掉 @SonicTitle");
            }
        }
    }

    /**
     * 整行都是空的（含"看起来是空的"那种：Excel 里选中过、设过格式的行）。
     *
     * <p>不过滤的话会出现"导入了 3 条却提示处理了 5000 行"。
     */
    boolean isBlankRow(Row row) {
        for (int position : positions) {
            if (CellCoercion.text(cellAt(row, position)) != null) {
                return false;
            }
        }
        return true;
    }

    /**
     * 取单元格，越界返回 null。
     *
     * <p>{@code Row#getCell(int)} 在行比请求下标短时会直接抛 IndexOutOfBounds ——
     * 而"最后几列全空所以这一行根本没那么多单元格"是 xlsx 里最普通不过的形态，
     * 不能让它变成异常。
     */
    private static Cell cellAt(Row row, int position) {
        if (position < 0 || position >= row.getCellCount()) {
            return null;
        }
        return row.getCell(position);
    }

    @SuppressWarnings("unchecked")
    T map(Row row, RowErrorSink sink) {
        List<ColumnMeta> columns = meta.columns();
        Object[] values = new Object[columns.size()];
        for (int i = 0; i < columns.size(); i++) {
            ColumnMeta col = columns.get(i);
            int position = positions[i];
            Cell cell = cellAt(row, position);
            try {
                values[i] = read(cell, col, row.getRowNum() - 1, position);
            } catch (Exception e) {
                sink.report(row.getRowNum() - 1, col.title(), CellCoercion.text(cell), e);
                return null;
            }
        }
        try {
            return (T) instantiate(values);
        } catch (Throwable t) {
            sink.report(row.getRowNum() - 1, "", null, t);
            return null;
        }
    }

    private Object read(Cell cell, ColumnMeta col, int rowIndex, int columnIndex) {
        SonicConverter<Object, Object> converter = col.converter();
        if (converter instanceof SonicConverter.None) {
            return CellCoercion.toJavaType(cell, col.javaType());
        }
        // 挂了转换器时喂原文：字典/枚举转换器要的就是 Excel 里那串标签
        String raw = CellCoercion.text(cell);
        Object converted = converter.importConvert(raw,
                new SonicContext(rowIndex, columnIndex, col.title(), col.javaType(), col.element(), null));
        return CellCoercion.align(converted, col.javaType());
    }

    private Object instantiate(Object[] values) throws Throwable {
        List<ColumnMeta> columns = meta.columns();
        return switch (meta.constructor()) {
            case RowConstructor.RecordCanonical(var constructor, var componentTypes) -> {
                Object[] args = new Object[componentTypes.size()];
                // 先按组件类型铺默认值：没被 @SonicTitle 标注的组件也得有值，
                // 而基本类型组件填 null 会让 invokeWithArguments 直接 NPE
                for (int i = 0; i < args.length; i++) {
                    args[i] = CellCoercion.defaultValue(componentTypes.get(i));
                }
                for (int i = 0; i < columns.size(); i++) {
                    ColumnMeta col = columns.get(i);
                    Object v = values[i];
                    // 缺值时基本类型只能落回 0 / false —— 这正是 MetaResolver 要在启动期拦截的语义污染
                    if (v != null) {
                        args[col.componentIndex()] = v;
                    }
                }
                yield constructor.invokeWithArguments(args);
            }
            case RowConstructor.PojoNoArg(var constructor) -> {
                Object target = constructor.invoke();
                for (int i = 0; i < columns.size(); i++) {
                    ColumnMeta col = columns.get(i);
                    Object v = values[i];
                    if (v == null && col.javaType().isPrimitive()) {
                        continue;
                    }
                    if (v != null) {
                        col.setter().accept(target, v);
                    }
                }
                yield target;
            }
            case RowConstructor.Unavailable(String reason) -> throw new SonicExcelException(reason);
        };
    }

    /**
     * 行级错误的去处，由 {@link SonicSheetReader} 按策略实现。
     */
    interface RowErrorSink {
        void report(int rowIndex, String title, String rawValue, Throwable cause);
    }
}
