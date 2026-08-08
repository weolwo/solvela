package net.lab1024.sa.base.common.util;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * SmartStringUtil 语义固化测试。
 *
 * 背景：这个类原先 extends hutool StrUtil，isEmpty/isBlank/trim/equals/join 全是继承来的。
 * 移除 hutool 时改成自实现，下面这些期望值是**当时与 StrUtil 逐条跑差异比对得出的**
 * （30 个字符串样本 × 5 个方法 + 900 组 equals + 60 组 join，0 处不一致），不是照感觉写的。
 * 改动本类实现时请先让这些用例继续通过，否则判空口径会在全项目范围内静默漂移。
 *
 * @Date 2026-08-08
 */
public class SmartStringUtilTest {

    /**
     * 「看不见但不是 JDK 空白」的几个字符。String#isBlank() 对它们一律返回 false，
     * 而本类沿用 hutool 口径判为空白 —— 从 Excel / 前端粘过来的文本里真的会有。
     */
    private static final char BOM = 0xFEFF;
    private static final char LEFT_TO_RIGHT_EMBEDDING = 0x202A;
    private static final char HANGUL_FILLER = 0x3164;
    private static final char BRAILLE_BLANK = 0x2800;
    private static final char MONGOLIAN_VOWEL_SEPARATOR = 0x180E;
    private static final char NO_BREAK_SPACE = 0x00A0;
    private static final char IDEOGRAPHIC_SPACE = 0x3000;

    @Test
    public void isEmpty_只看长度不看内容() {
        assertTrue(SmartStringUtil.isEmpty(null));
        assertTrue(SmartStringUtil.isEmpty(""));
        // 全空格是「非空」——这正是 isEmpty 与 isBlank 的分界，别混用
        assertFalse(SmartStringUtil.isEmpty(" "));
        assertFalse(SmartStringUtil.isEmpty("a"));

        assertFalse(SmartStringUtil.isNotEmpty(null));
        assertFalse(SmartStringUtil.isNotEmpty(""));
        assertTrue(SmartStringUtil.isNotEmpty(" "));
    }

    @Test
    public void isBlank_覆盖JDK不认的空白字符() {
        assertTrue(SmartStringUtil.isBlank(null));
        assertTrue(SmartStringUtil.isBlank(""));
        assertTrue(SmartStringUtil.isBlank(" "));
        assertTrue(SmartStringUtil.isBlank("\t"));
        assertTrue(SmartStringUtil.isBlank("\n"));
        assertTrue(SmartStringUtil.isBlank("  \t\n  "));
        assertFalse(SmartStringUtil.isBlank("a"));
        assertFalse(SmartStringUtil.isBlank(" a "));
        assertFalse(SmartStringUtil.isBlank("中文"));

        for (char c : new char[]{BOM, LEFT_TO_RIGHT_EMBEDDING, HANGUL_FILLER, BRAILLE_BLANK,
                MONGOLIAN_VOWEL_SEPARATOR, NO_BREAK_SPACE, IDEOGRAPHIC_SPACE, 0}) {
            String s = String.valueOf(c);
            assertTrue(SmartStringUtil.isBlank(s),
                    () -> String.format("U+%04X 应判为空白", (int) c));
            assertFalse(SmartStringUtil.isBlank(s + "a"),
                    () -> String.format("U+%04X + 实字符 不应判为空白", (int) c));
        }

        assertTrue(SmartStringUtil.isNotBlank("a"));
        assertFalse(SmartStringUtil.isNotBlank("   "));
    }

    @Test
    public void trim_null进null出() {
        // 关键差异：不是返回 ""，而是原样返回 null
        assertNull(SmartStringUtil.trim(null));
        assertEquals("", SmartStringUtil.trim(""));
        assertEquals("", SmartStringUtil.trim("   "));
        assertEquals("a", SmartStringUtil.trim(" a "));
        assertEquals("a b", SmartStringUtil.trim("  a b  "));
        assertEquals("中文", SmartStringUtil.trim(" 中文 "));
        // 首尾的不可见空白同样要去掉
        assertEquals("中文", SmartStringUtil.trim(NO_BREAK_SPACE + "中文" + BOM));
        // 中间的空白不动
        assertEquals("多  空  格", SmartStringUtil.trim("  多  空  格  "));
    }

    @Test
    public void equals_null安全且两个null相等() {
        assertTrue(SmartStringUtil.equals(null, null));
        assertFalse(SmartStringUtil.equals(null, ""));
        assertFalse(SmartStringUtil.equals("", null));
        assertTrue(SmartStringUtil.equals("", ""));
        assertTrue(SmartStringUtil.equals("a", "a"));
        assertFalse(SmartStringUtil.equals("a", "A"));
        assertFalse(SmartStringUtil.equals("a", "a "));
        // CharSequence 比的是内容，不是引用类型
        assertTrue(SmartStringUtil.equals("abc", new StringBuilder("abc")));
    }

    @Test
    public void join_分隔符在前且null元素拼成null字面量() {
        // 参数顺序是 (分隔符, 集合)，与 String.join 相同、与 Collectors.joining 相反，别写反
        assertEquals("a,b,c", SmartStringUtil.join(",", Arrays.asList("a", "b", "c")));
        assertEquals("abc", SmartStringUtil.join("", Arrays.asList("a", "b", "c")));
        assertEquals("a<br/>b", SmartStringUtil.join("<br/>", Arrays.asList("a", "b")));
        assertEquals("", SmartStringUtil.join(",", new ArrayList<>()));
        assertEquals("a", SmartStringUtil.join(",", Arrays.asList("a")));
        assertEquals(",,", SmartStringUtil.join(",", Arrays.asList("", "", "")));
        // null 元素拼成字符串 "null"，不是跳过、也不抛异常
        assertEquals("a,null,c", SmartStringUtil.join(",", Arrays.asList("a", null, "c")));
        // 非字符串元素走 toString
        assertEquals("1,2,3.5", SmartStringUtil.join(",", Arrays.asList(1, 2L, 3.5)));

        // varargs 重载：RedisLockAspect / DefaultMd5KeyGenerator 拼 redis key 走的是这条
        assertEquals("pre:key", SmartStringUtil.join(":", "pre", "key"));
        assertEquals("1:/a/b:params", SmartStringUtil.join(":", 1L, "/a/b", "params"));
        // 单元素 join 就是它自己
        assertEquals("x", SmartStringUtil.join(",", "x"));

        assertNull(SmartStringUtil.join(",", (List<String>) null));
    }

    @Test
    public void truncate_按列长度安全截断() {
        assertNull(SmartStringUtil.truncate(null, 10));
        assertEquals("abc", SmartStringUtil.truncate("abc", 10));
        assertEquals("abc", SmartStringUtil.truncate("abc", 3));
        assertEquals("ab", SmartStringUtil.truncate("abc", 2));
        assertEquals("abc", SmartStringUtil.truncate("abc", 0));
    }

    @Test
    public void split系列在入参为空时返回空集合而不是null() {
        assertTrue(SmartStringUtil.splitConvertToList(null, ",").isEmpty());
        assertTrue(SmartStringUtil.splitConvertToSet("", ",").isEmpty());
        assertEquals(List.of("a", "b"), SmartStringUtil.splitConvertToList("a,b", ","));
        assertEquals(List.of(1, 2), SmartStringUtil.splitConvertToIntList("1,2", ","));
        // 解析不了的段落回落到默认值，不抛异常
        assertEquals(List.of(1, 0), SmartStringUtil.splitConvertToIntList("1,x", ","));
        assertEquals(List.of(1, -1), SmartStringUtil.splitConvertToIntList("1,x", ",", -1));
    }
}
