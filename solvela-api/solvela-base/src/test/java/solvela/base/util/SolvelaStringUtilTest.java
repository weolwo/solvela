package solvela.base.util;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * SolvelaStringUtil 语义固化测试。
 * <p>
 * 背景：这个类原先 extends hutool StrUtil，isEmpty/isBlank/trim/equals/join 全是继承来的。
 * 移除 hutool 时改成自实现，下面这些期望值是**当时与 StrUtil 逐条跑差异比对得出的**
 * （30 个字符串样本 × 5 个方法 + 900 组 equals + 60 组 join，0 处不一致），不是照感觉写的。
 * 改动本类实现时请先让这些用例继续通过，否则判空口径会在全项目范围内静默漂移。
 *
 * @Date 2026-08-08
 */
public class SolvelaStringUtilTest {

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
        assertTrue(SolvelaStringUtil.isEmpty(null));
        assertTrue(SolvelaStringUtil.isEmpty(""));
        // 全空格是「非空」——这正是 isEmpty 与 isBlank 的分界，别混用
        assertFalse(SolvelaStringUtil.isEmpty(" "));
        assertFalse(SolvelaStringUtil.isEmpty("a"));

        assertFalse(SolvelaStringUtil.isNotEmpty(null));
        assertFalse(SolvelaStringUtil.isNotEmpty(""));
        assertTrue(SolvelaStringUtil.isNotEmpty(" "));
    }

    @Test
    public void isBlank_覆盖JDK不认的空白字符() {
        assertTrue(SolvelaStringUtil.isBlank(null));
        assertTrue(SolvelaStringUtil.isBlank(""));
        assertTrue(SolvelaStringUtil.isBlank(" "));
        assertTrue(SolvelaStringUtil.isBlank("\t"));
        assertTrue(SolvelaStringUtil.isBlank("\n"));
        assertTrue(SolvelaStringUtil.isBlank("  \t\n  "));
        assertFalse(SolvelaStringUtil.isBlank("a"));
        assertFalse(SolvelaStringUtil.isBlank(" a "));
        assertFalse(SolvelaStringUtil.isBlank("中文"));

        for (char c : new char[]{BOM, LEFT_TO_RIGHT_EMBEDDING, HANGUL_FILLER, BRAILLE_BLANK,
                MONGOLIAN_VOWEL_SEPARATOR, NO_BREAK_SPACE, IDEOGRAPHIC_SPACE, 0}) {
            String s = String.valueOf(c);
            assertTrue(SolvelaStringUtil.isBlank(s),
                    () -> String.format("U+%04X 应判为空白", (int) c));
            assertFalse(SolvelaStringUtil.isBlank(s + "a"),
                    () -> String.format("U+%04X + 实字符 不应判为空白", (int) c));
        }

        assertTrue(SolvelaStringUtil.isNotBlank("a"));
        assertFalse(SolvelaStringUtil.isNotBlank("   "));
    }

    @Test
    public void trim_null进null出() {
        // 关键差异：不是返回 ""，而是原样返回 null
        assertNull(SolvelaStringUtil.trim(null));
        assertEquals("", SolvelaStringUtil.trim(""));
        assertEquals("", SolvelaStringUtil.trim("   "));
        assertEquals("a", SolvelaStringUtil.trim(" a "));
        assertEquals("a b", SolvelaStringUtil.trim("  a b  "));
        assertEquals("中文", SolvelaStringUtil.trim(" 中文 "));
        // 首尾的不可见空白同样要去掉
        assertEquals("中文", SolvelaStringUtil.trim(NO_BREAK_SPACE + "中文" + BOM));
        // 中间的空白不动
        assertEquals("多  空  格", SolvelaStringUtil.trim("  多  空  格  "));
    }

    @Test
    public void equals_null安全且两个null相等() {
        assertTrue(SolvelaStringUtil.equals(null, null));
        assertFalse(SolvelaStringUtil.equals(null, ""));
        assertFalse(SolvelaStringUtil.equals("", null));
        assertTrue(SolvelaStringUtil.equals("", ""));
        assertTrue(SolvelaStringUtil.equals("a", "a"));
        assertFalse(SolvelaStringUtil.equals("a", "A"));
        assertFalse(SolvelaStringUtil.equals("a", "a "));
        // CharSequence 比的是内容，不是引用类型
        assertTrue(SolvelaStringUtil.equals("abc", new StringBuilder("abc")));
    }

    @Test
    public void join_分隔符在前且null元素拼成null字面量() {
        // 参数顺序是 (分隔符, 集合)，与 String.join 相同、与 Collectors.joining 相反，别写反
        assertEquals("a,b,c", SolvelaStringUtil.join(",", Arrays.asList("a", "b", "c")));
        assertEquals("abc", SolvelaStringUtil.join("", Arrays.asList("a", "b", "c")));
        assertEquals("a<br/>b", SolvelaStringUtil.join("<br/>", Arrays.asList("a", "b")));
        assertEquals("", SolvelaStringUtil.join(",", new ArrayList<>()));
        assertEquals("a", SolvelaStringUtil.join(",", Arrays.asList("a")));
        assertEquals(",,", SolvelaStringUtil.join(",", Arrays.asList("", "", "")));
        // null 元素拼成字符串 "null"，不是跳过、也不抛异常
        assertEquals("a,null,c", SolvelaStringUtil.join(",", Arrays.asList("a", null, "c")));
        // 非字符串元素走 toString
        assertEquals("1,2,3.5", SolvelaStringUtil.join(",", Arrays.asList(1, 2L, 3.5)));

        // varargs 重载：RedisLockAspect / DefaultMd5KeyGenerator 拼 redis key 走的是这条
        assertEquals("pre:key", SolvelaStringUtil.join(":", "pre", "key"));
        assertEquals("1:/a/b:params", SolvelaStringUtil.join(":", 1L, "/a/b", "params"));
        // 单元素 join 就是它自己
        assertEquals("x", SolvelaStringUtil.join(",", "x"));

        assertNull(SolvelaStringUtil.join(",", (List<String>) null));
    }

    /**
     * U+1F600，一个需要代理对表示的字符
     */
    private static final String EMOJI = new String(Character.toChars(0x1F600));

    @Test
    public void hide_按码点打码_越界一律返回原串() {
        assertEquals("a****", SolvelaStringUtil.hide("abcde", 1, 5));
        assertEquals("abcde", SolvelaStringUtil.hide("abcde", 3, 2), "start > end 时原样返回");
        assertEquals("abcde", SolvelaStringUtil.hide("abcde", 9, 20), "start 越界时原样返回");
        assertEquals("a****", SolvelaStringUtil.hide("abcde", 1, 99), "end 越界时截到末尾");
        assertEquals("", SolvelaStringUtil.hide("", 0, 3));
        assertNull(SolvelaStringUtil.hide(null, 0, 3));
        // 越界一律返回原串而不是抛异常是刻意的：脱敏发生在 JSON 序列化途中，
        // 为一个短字符串炸掉整个响应不值当
        assertEquals("abc", SolvelaStringUtil.hide("abc", 10, 20));

        // 一个 emoji 是两个 char，按码点打码才不会把代理对劈开成乱码方块
        assertEquals(EMOJI + "***" + EMOJI, SolvelaStringUtil.hide(EMOJI.repeat(5), 1, 4));
    }

    @Test
    public void cleanBlank_去掉所有空白而不只是首尾() {
        assertEquals("6217000010001234", SolvelaStringUtil.cleanBlank("  6217 0000 1000 1234  "));
        assertEquals("abc", SolvelaStringUtil.cleanBlank("a b	c"));
        assertEquals("", SolvelaStringUtil.cleanBlank("   "));
        assertNull(SolvelaStringUtil.cleanBlank(null));
        // 不可见空白同样清掉
        assertEquals("ab", SolvelaStringUtil.cleanBlank("a" + NO_BREAK_SPACE + "b"));
    }

    @Test
    public void truncate_按列长度安全截断() {
        assertNull(SolvelaStringUtil.truncate(null, 10));
        assertEquals("abc", SolvelaStringUtil.truncate("abc", 10));
        assertEquals("abc", SolvelaStringUtil.truncate("abc", 3));
        assertEquals("ab", SolvelaStringUtil.truncate("abc", 2));
        assertEquals("abc", SolvelaStringUtil.truncate("abc", 0));
    }

    @Test
    public void split系列在入参为空时返回空集合而不是null() {
        assertTrue(SolvelaStringUtil.splitConvertToList(null, ",").isEmpty());
        assertTrue(SolvelaStringUtil.splitConvertToSet("", ",").isEmpty());
        assertEquals(List.of("a", "b"), SolvelaStringUtil.splitConvertToList("a,b", ","));
        assertEquals(List.of(1, 2), SolvelaStringUtil.splitConvertToIntList("1,2", ","));
        // 解析不了的段落回落到默认值，不抛异常
        assertEquals(List.of(1, 0), SolvelaStringUtil.splitConvertToIntList("1,x", ","));
        assertEquals(List.of(1, -1), SolvelaStringUtil.splitConvertToIntList("1,x", ",", -1));
    }
}
