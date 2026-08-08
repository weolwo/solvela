package net.lab1024.sa.base.sonicexcel.read;

import org.dhatim.fastexcel.reader.Cell;
import org.dhatim.fastexcel.reader.CellType;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.List;

/**
 * 单元格 → Java 类型。
 *
 * <p>核心取舍：<b>不直接用 {@code Cell#asNumber()} 这类强类型取值器</b>。
 * 它们在类型不符时直接抛 {@code ExcelReaderException}，而真实上传文件里
 * 「数字被存成文本」「日期被存成文本」是常态（用户粘贴、系统导出、WPS 另存都会这样）。
 * 所以按目标类型走：类型对得上就取原生值，对不上就退回文本解析，都失败才算这一格坏了。
 *
 * @Date 2026-08-08
 */
final class CellCoercion {

    /**
     * 文本日期的常见写法。用户从别处粘进来的日期就是这几种。
     */
    private static final List<DateTimeFormatter> DATE_TIME_PATTERNS = List.of(
            DateTimeFormatter.ISO_LOCAL_DATE_TIME,
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));

    private static final List<DateTimeFormatter> DATE_PATTERNS = List.of(
            DateTimeFormatter.ISO_LOCAL_DATE,
            DateTimeFormatter.ofPattern("yyyy/MM/dd"),
            DateTimeFormatter.ofPattern("yyyyMMdd"));

    private static final List<String> TRUE_WORDS = List.of("true", "1", "是", "y", "yes", "√", "对");
    private static final List<String> FALSE_WORDS = List.of("false", "0", "否", "n", "no", "×", "错");

    private CellCoercion() {
    }

    /**
     * 单元格原文。空单元格返回 null 而不是空串 —— 空行判定和"这一列没填"都依赖这个区分。
     */
    static String text(Cell cell) {
        if (cell == null || cell.getType() == CellType.EMPTY) {
            return null;
        }
        String s = cell.getText();
        return HeaderMatcher.isBlank(s) ? null : HeaderMatcher.normalize(s);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    static Object toJavaType(Cell cell, Class<?> target) {
        if (target == String.class) {
            return text(cell);
        }
        String raw = text(cell);
        if (raw == null) {
            return null;
        }
        if (target == BigDecimal.class) {
            return number(cell, raw);
        }
        if (target == Integer.class || target == int.class) {
            return number(cell, raw).intValueExact();
        }
        if (target == Long.class || target == long.class) {
            return number(cell, raw).longValueExact();
        }
        if (target == Short.class || target == short.class) {
            return number(cell, raw).shortValueExact();
        }
        if (target == Byte.class || target == byte.class) {
            return number(cell, raw).byteValueExact();
        }
        if (target == Double.class || target == double.class) {
            return number(cell, raw).doubleValue();
        }
        if (target == Float.class || target == float.class) {
            return number(cell, raw).floatValue();
        }
        if (target == BigInteger.class) {
            return number(cell, raw).toBigIntegerExact();
        }
        if (target == Boolean.class || target == boolean.class) {
            return bool(cell, raw);
        }
        if (target == LocalDateTime.class) {
            return dateTime(cell, raw);
        }
        if (target == LocalDate.class) {
            return dateTime(cell, raw).toLocalDate();
        }
        if (target == LocalTime.class) {
            return dateTime(cell, raw).toLocalTime();
        }
        if (target == Date.class) {
            return Date.from(dateTime(cell, raw).atZone(ZoneId.systemDefault()).toInstant());
        }
        if (target.isEnum()) {
            return Enum.valueOf((Class<? extends Enum>) target, raw);
        }
        return raw;
    }

    /**
     * 把转换器吐出来的东西对齐到字段类型。转换器返回 String 而字段是 Integer 是常见写法，
     * 不该因此报错。
     */
    static Object align(Object value, Class<?> target) {
        if (value == null) {
            return null;
        }
        if (box(target).isInstance(value)) {
            return value;
        }
        return fromText(String.valueOf(value), target);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Object fromText(String raw, Class<?> target) {
        if (target == String.class) {
            return raw;
        }
        if (target == BigDecimal.class) {
            return new BigDecimal(raw);
        }
        if (target == Integer.class || target == int.class) {
            return Integer.valueOf(raw);
        }
        if (target == Long.class || target == long.class) {
            return Long.valueOf(raw);
        }
        if (target == Boolean.class || target == boolean.class) {
            return parseBoolean(raw);
        }
        if (target.isEnum()) {
            return Enum.valueOf((Class<? extends Enum>) target, raw);
        }
        return raw;
    }

    private static BigDecimal number(Cell cell, String raw) {
        if (cell.getType() == CellType.NUMBER) {
            return cell.asNumber();
        }
        // 千分位是用户手输的常见形态
        return new BigDecimal(raw.replace(",", "").replace("，", ""));
    }

    private static Boolean bool(Cell cell, String raw) {
        if (cell.getType() == CellType.BOOLEAN) {
            return cell.asBoolean();
        }
        return parseBoolean(raw);
    }

    private static Boolean parseBoolean(String raw) {
        String v = raw.toLowerCase();
        if (TRUE_WORDS.contains(v)) {
            return Boolean.TRUE;
        }
        if (FALSE_WORDS.contains(v)) {
            return Boolean.FALSE;
        }
        throw new IllegalArgumentException("无法识别的布尔值：" + raw);
    }

    private static LocalDateTime dateTime(Cell cell, String raw) {
        if (cell.getType() == CellType.NUMBER || cell.getType() == CellType.FORMULA) {
            return cell.asDate();
        }
        for (DateTimeFormatter f : DATE_TIME_PATTERNS) {
            try {
                return LocalDateTime.parse(raw, f);
            } catch (RuntimeException ignored) {
                // 换下一个格式
            }
        }
        for (DateTimeFormatter f : DATE_PATTERNS) {
            try {
                return LocalDate.parse(raw, f).atStartOfDay();
            } catch (RuntimeException ignored) {
                // 换下一个格式
            }
        }
        throw new IllegalArgumentException("无法识别的日期：" + raw);
    }

    static Object defaultValue(Class<?> t) {
        if (!t.isPrimitive()) {
            return null;
        }
        return switch (t.getName()) {
            case "int" -> 0;
            case "long" -> 0L;
            case "double" -> 0d;
            case "float" -> 0f;
            case "short" -> (short) 0;
            case "byte" -> (byte) 0;
            case "char" -> '\0';
            case "boolean" -> Boolean.FALSE;
            default -> null;
        };
    }

    private static Class<?> box(Class<?> t) {
        if (!t.isPrimitive()) {
            return t;
        }
        return switch (t.getName()) {
            case "int" -> Integer.class;
            case "long" -> Long.class;
            case "double" -> Double.class;
            case "float" -> Float.class;
            case "short" -> Short.class;
            case "byte" -> Byte.class;
            case "char" -> Character.class;
            case "boolean" -> Boolean.class;
            default -> t;
        };
    }
}
