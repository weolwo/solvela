package solvela.base.sonicexcel.write;

import solvela.base.sonicexcel.annotation.SonicOptions;
import solvela.base.sonicexcel.converter.SonicContext;
import solvela.base.sonicexcel.converter.SonicConverterFactory;
import solvela.base.sonicexcel.meta.ColumnMeta;
import solvela.base.sonicexcel.meta.MetaResolver;
import solvela.base.sonicexcel.meta.SheetMeta;
import solvela.base.sonicexcel.option.SonicOptionProvider;
import org.dhatim.fastexcel.Workbook;
import org.dhatim.fastexcel.Worksheet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.util.List;

/**
 * 导入模板生成：表头 + 可选的示例行 + 下拉校验。
 *
 * <p>下拉是"防脏数据"最划算的一招 —— 用户根本填不出非法值，比事后报错强得多。
 *
 * @Date 2026-08-08
 */
public final class SonicTemplateWriter<T> {

    private static final Logger log = LoggerFactory.getLogger(SonicTemplateWriter.class);

    /**
     * Excel 对内联下拉列表的 formula1 有 255 字符上限，超了文件会被判定损坏。
     */
    private static final int MAX_INLINE_OPTIONS_LENGTH = 255;

    /**
     * 下拉的作用行数。给足以后用户往下拖的余量，又不至于让文件变大（数据有效性只是一段 XML）。
     */
    private static final int VALIDATION_ROWS = 5000;

    private final OutputStream out;
    private final SheetMeta meta;

    private String sheetName = "导入模板";
    private List<String> sampleRow = List.of();

    public SonicTemplateWriter(OutputStream out, Class<T> head) {
        this.out = out;
        this.meta = MetaResolver.resolve(head);
    }

    public SonicTemplateWriter<T> sheet(String name) {
        this.sheetName = name;
        return this;
    }

    /**
     * 示例行。按列顺序给，给几个算几个 —— 让用户一眼看出每列该填什么形态。
     */
    public SonicTemplateWriter<T> sample(String... values) {
        this.sampleRow = List.of(values);
        return this;
    }

    public void write() {
        List<ColumnMeta> columns = meta.columns();
        try (Workbook workbook = new Workbook(out, "Solvela", "3.0")) {
            Worksheet sheet = workbook.newWorksheet(sheetName);
            ColumnWidths widths = new ColumnWidths(meta);

            for (int c = 0; c < columns.size(); c++) {
                ColumnMeta col = columns.get(c);
                sheet.inlineString(0, c, col.title());
                sheet.style(0, c).bold().set();
                sheet.width(c, widths.widthOf(c));
                if (c < sampleRow.size()) {
                    sheet.inlineString(1, c, sampleRow.get(c));
                }
                applyDropdown(sheet, c, col);
            }
            sheet.freezePane(0, 1);
            sheet.finish();
        } catch (IOException e) {
            throw new UncheckedIOException("SonicExcel 模板生成失败", e);
        }
    }

    private void applyDropdown(Worksheet sheet, int column, ColumnMeta col) {
        List<String> options = optionsOf(col, column);
        if (options.isEmpty()) {
            return;
        }
        // 字面量列表不能含逗号和引号，否则会把 formula1 撕开
        List<String> safe = options.stream()
                .filter(o -> o != null && !o.isEmpty() && o.indexOf(',') < 0 && o.indexOf('"') < 0)
                .toList();
        if (safe.size() != options.size()) {
            log.warn("[SonicExcel] 列「{}」的选项含逗号或引号，无法作为内联下拉，已跳过这些值", col.title());
        }
        if (safe.isEmpty()) {
            return;
        }
        String formula = "\"" + String.join(",", safe) + "\"";
        if (formula.length() > MAX_INLINE_OPTIONS_LENGTH) {
            log.warn("[SonicExcel] 列「{}」的选项过长（{} 字符 > {}），Excel 内联下拉放不下，已跳过",
                    col.title(), formula.length(), MAX_INLINE_OPTIONS_LENGTH);
            return;
        }
        sheet.range(1, column, VALIDATION_ROWS, column)
                .validateWithListByFormula(formula)
                .allowBlank(true)
                .showDropdown(true)
                .showErrorMessage(true)
                .errorTitle("取值不合法")
                .error("请从下拉列表中选择");
    }

    private List<String> optionsOf(ColumnMeta col, int column) {
        SonicOptions annotation = col.element().getAnnotation(SonicOptions.class);
        if (annotation == null) {
            return List.of();
        }
        if (annotation.value().length > 0) {
            return List.of(annotation.value());
        }
        if (annotation.provider() == SonicOptionProvider.None.class) {
            return List.of();
        }
        SonicOptionProvider provider = SonicConverterFactory.resolveExtension(annotation.provider());
        List<String> options = provider.options(
                new SonicContext(0, column, col.title(), col.javaType(), col.element(), null));
        return options == null ? List.of() : options;
    }
}
