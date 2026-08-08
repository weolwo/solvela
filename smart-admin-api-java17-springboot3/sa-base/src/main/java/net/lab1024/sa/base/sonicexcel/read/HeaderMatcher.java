package net.lab1024.sa.base.sonicexcel.read;

import net.lab1024.sa.base.sonicexcel.meta.ColumnMeta;
import net.lab1024.sa.base.sonicexcel.meta.SheetMeta;
import org.dhatim.fastexcel.reader.Row;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 表头动态寻址：<b>无视用户上传模板的列顺序错乱</b>。
 *
 * <p>读表头行建「归一化标题 → 实际列下标」，再按 {@link SheetMeta} 的列去查。
 * 多出来的列忽略，缺的列留空。
 *
 * @Date 2026-08-08
 */
final class HeaderMatcher {

    private HeaderMatcher() {
    }

    /**
     * @return 长度等于列数的数组，第 i 位是该列在 Excel 中的实际下标，-1 表示这一列在上传的文件里不存在
     */
    static int[] match(SheetMeta meta, Row header) {
        Map<String, Integer> exact = new HashMap<>();
        Map<String, Integer> ignoreCase = new HashMap<>();
        for (int c = 0; c < header.getCellCount(); c++) {
            String raw = header.getCellText(c);
            if (raw == null) {
                continue;
            }
            String key = normalize(raw);
            if (key.isEmpty()) {
                continue;
            }
            exact.putIfAbsent(key, c);
            ignoreCase.putIfAbsent(key.toLowerCase(), c);
        }

        List<ColumnMeta> columns = meta.columns();
        int[] positions = new int[columns.size()];
        for (int i = 0; i < columns.size(); i++) {
            positions[i] = locate(columns.get(i), exact, ignoreCase);
        }
        return positions;
    }

    private static int locate(ColumnMeta column, Map<String, Integer> exact, Map<String, Integer> ignoreCase) {
        Integer hit = exact.get(normalize(column.title()));
        if (hit != null) {
            return hit;
        }
        // alias 是刚需：中文表头改一次字，用户手里所有旧模板就全部导入失败
        for (String alias : column.alias()) {
            hit = exact.get(normalize(alias));
            if (hit != null) {
                return hit;
            }
        }
        hit = ignoreCase.get(normalize(column.title()).toLowerCase());
        if (hit != null) {
            return hit;
        }
        for (String alias : column.alias()) {
            hit = ignoreCase.get(normalize(alias).toLowerCase());
            if (hit != null) {
                return hit;
            }
        }
        return -1;
    }

    /**
     * 去掉首尾的空白类字符。
     *
     * <p>不能只用 {@code String#trim()} 或 {@code strip()}：不间断空格 U+00A0、BOM U+FEFF、
     * 全角空格 U+3000 都不算标准空白，而它们恰恰最常出现在用户从网页粘进 Excel 的表头里
     * （交接文档 §10 记过同一个坑：{@code isBlank} 的口径漂移）。
     */
    static String normalize(String s) {
        int begin = 0;
        int end = s.length();
        while (begin < end && isBlankish(s.charAt(begin))) {
            begin++;
        }
        while (end > begin && isBlankish(s.charAt(end - 1))) {
            end--;
        }
        return s.substring(begin, end);
    }

    static boolean isBlankish(char c) {
        // 用转义而不是字面量：这三个字符在编辑器里是隐形的，写成字面量后没人看得出改动。
        // Character.isWhitespace 对 NBSP 和 BOM 都返回 false，必须单列出来
        return Character.isWhitespace(c)
                || c == ' '   // NO-BREAK SPACE，网页复制粘贴的常客
                || c == '﻿'   // BOM / ZERO WIDTH NO-BREAK SPACE
                || c == '　';  // 全角空格
    }

    /**
     * 整串都是空白类字符（含完全为空）。空行判定用的也是这个口径。
     */
    static boolean isBlank(String s) {
        if (s == null || s.isEmpty()) {
            return true;
        }
        for (int i = 0; i < s.length(); i++) {
            if (!isBlankish(s.charAt(i))) {
                return false;
            }
        }
        return true;
    }
}
