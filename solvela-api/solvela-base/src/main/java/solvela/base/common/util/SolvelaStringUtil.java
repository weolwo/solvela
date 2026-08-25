package solvela.base.common.util;


import java.util.*;

/**
 * 独有的字符串工具类
 *
 * 原先 extends cn.hutool.core.util.StrUtil，靠静态方法继承对外提供 isEmpty/isBlank/join/trim/equals。
 * 移除 hutool 后这些方法在本类内自实现，语义与 StrUtil 保持一致（见各方法注释），
 * 调用方一行都不用改。
 *
 * @Author 1024创新实验室-主任: 卓大
 * @Date 2021-09-02 20:21:10
 * @Wechat zhuoda1024
 * @Email lab1024@163.com
 * @Copyright  <a href="https://1024lab.net">1024创新实验室</a>
 */
public class SolvelaStringUtil {

    // ===============判空 / 比较 / 拼接（原 StrUtil 继承而来）=======================

    /**
     * 是否为空：null 或长度为 0。
     * 注意与 {@link #isBlank} 的区别：全空格的字符串在这里是「非空」。
     */
    public static boolean isEmpty(CharSequence str) {
        return str == null || str.isEmpty();
    }

    public static boolean isNotEmpty(CharSequence str) {
        return !isEmpty(str);
    }

    /**
     * 是否为空白：null、长度为 0，或全部由空白字符组成。
     *
     * 没有直接用 String#isBlank()：JDK 只认 Character.isWhitespace，
     * 而不间断空格 U+00A0、零宽 BOM U+FEFF 这些在它眼里都不是空白 ——
     * 从前端/Excel 粘过来的文本里恰恰常有这类字符。此处沿用 hutool 的判定范围。
     */
    public static boolean isBlank(CharSequence str) {
        if (isEmpty(str)) {
            return true;
        }
        for (int i = 0; i < str.length(); i++) {
            if (!isBlankChar(str.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    public static boolean isNotBlank(CharSequence str) {
        return !isBlank(str);
    }

    /**
     * 空白字符判定。除 JDK 认的空白外，还包含几个「看不见但不是空白」的字符：
     * BOM、从左至右嵌入符、韩文填充符、盲文空格、蒙古文元音分隔符 —— 与 hutool CharUtil 口径一致。
     * 这里一律写码点常量，不写字面量：这些字符在编辑器里不可见，写进源码没人看得出来。
     */
    private static boolean isBlankChar(char c) {
        return Character.isWhitespace(c)
                || Character.isSpaceChar(c)
                || c == 0x0000
                || c == 0xFEFF
                || c == 0x202A
                || c == 0x3164
                || c == 0x2800
                || c == 0x180E;
    }

    /**
     * 去除首尾空白，null 安全（入参 null 返回 null，不抛异常也不返回 ""）。
     * 空白字符的判定范围同 {@link #isBlank}。
     */
    public static String trim(CharSequence str) {
        if (str == null) {
            return null;
        }
        int start = 0;
        int end = str.length();
        while (start < end && isBlankChar(str.charAt(start))) {
            start++;
        }
        while (end > start && isBlankChar(str.charAt(end - 1))) {
            end--;
        }
        return str.toString().substring(start, end);
    }

    /**
     * null 安全的相等判断：两个都为 null 视为相等。
     */
    public static boolean equals(CharSequence str1, CharSequence str2) {
        if (str1 == null) {
            return str2 == null;
        }
        if (str2 == null) {
            return false;
        }
        return str1.toString().contentEquals(str2);
    }

    /**
     * 拼接，分隔符在前（保持 StrUtil.join 的参数顺序，别顺手改成 String.join 的写法）。
     * 元素为 null 时拼成 "null"，与 StrUtil 一致。
     */
    public static String join(CharSequence conjunction, Iterable<?> iterable) {
        if (iterable == null) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (Object item : iterable) {
            if (!first) {
                sb.append(conjunction);
            }
            sb.append(item);
            first = false;
        }
        return sb.toString();
    }

    public static String join(CharSequence conjunction, Object... objects) {
        return objects == null ? null : join(conjunction, Arrays.asList(objects));
    }

    /**
     * 去掉**所有**空白字符，不只是首尾（原 StrUtil.cleanBlank）。
     * 与 {@link #trim} 的区别是中间的也去掉：银行卡号 "6217 0000 1000 1234" 要先压成连续数字才好分组打码。
     */
    public static String cleanBlank(CharSequence str) {
        if (str == null) {
            return null;
        }
        StringBuilder sb = new StringBuilder(str.length());
        for (int i = 0; i < str.length(); i++) {
            char c = str.charAt(i);
            if (!isBlankChar(c)) {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    /**
     * 把 [startInclude, endExclude) 区间内的字符替换成 '*'（原 StrUtil.hide），脱敏用。
     *
     * 按**码点**而不是 char 遍历：一个 emoji 或生僻字是两个 char，
     * 按 char 打码会把代理对劈开，拼出乱码方块。
     *
     * 越界一律返回原串而不是抛异常：脱敏发生在序列化途中，为一个短字符串炸掉整个响应不值当。
     */
    public static String hide(CharSequence str, int startInclude, int endExclude) {
        if (isEmpty(str)) {
            return str == null ? null : str.toString();
        }
        String original = str.toString();
        int[] codePoints = original.codePoints().toArray();
        int length = codePoints.length;
        if (startInclude > length || startInclude > endExclude) {
            return original;
        }
        int end = Math.min(endExclude, length);
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            if (i >= startInclude && i < end) {
                sb.append('*');
            } else {
                sb.appendCodePoint(codePoints[i]);
            }
        }
        return sb.toString();
    }

    // ===============split =======================

    public static Set<String> splitConvertToSet(String str, String split) {
        if (isEmpty(str)) {
            return new HashSet<String>();
        }
        String[] splitArr = str.split(split);
        HashSet<String> set = new HashSet<String>(splitArr.length);
        Collections.addAll(set, splitArr);
        return set;
    }

    public static List<String> splitConvertToList(String str, String split) {
        if (isEmpty(str)) {
            return new ArrayList<String>();
        }
        String[] splitArr = str.split(split);
        ArrayList<String> list = new ArrayList<String>(splitArr.length);
        list.addAll(Arrays.asList(splitArr));
        return list;
    }

    // ===============split Integer=======================

    public static List<Integer> splitConvertToIntList(String str, String split, int defaultVal) {
        if (isEmpty(str)) {
            return new ArrayList<Integer>();
        }
        String[] strArr = str.split(split);
        List<Integer> list = new ArrayList<Integer>(strArr.length);
        for (int i = 0; i < strArr.length; i++) {
            try {
                int parseInt = Integer.parseInt(strArr[i]);
                list.add(parseInt);
            } catch (NumberFormatException e) {
                list.add(defaultVal);
                continue;
            }
        }
        return list;
    }

    public static Set<Integer> splitConvertToIntSet(String str, String split, int defaultVal) {
        if (isEmpty(str)) {
            return new HashSet<Integer>();
        }
        String[] strArr = str.split(split);
        HashSet<Integer> set = new HashSet<Integer>(strArr.length);
        for (int i = 0; i < strArr.length; i++) {
            try {
                int parseInt = Integer.parseInt(strArr[i]);
                set.add(parseInt);
            } catch (NumberFormatException e) {
                set.add(defaultVal);
                continue;
            }
        }
        return set;
    }

    public static Set<Integer> splitConvertToIntSet(String str, String split) {
        return splitConvertToIntSet(str, split, 0);
    }

    public static List<Integer> splitConvertToIntList(String str, String split) {
        return splitConvertToIntList(str, split, 0);
    }

    public static int[] splitConvertToIntArray(String str, String split, int defaultVal) {
        if (isEmpty(str)) {
            return new int[0];
        }
        String[] strArr = str.split(split);
        int[] result = new int[strArr.length];
        for (int i = 0; i < strArr.length; i++) {
            try {
                result[i] = Integer.parseInt(strArr[i]);
            } catch (NumberFormatException e) {
                result[i] = defaultVal;
                continue;
            }
        }
        return result;
    }

    public static int[] splitConvertToIntArray(String str, String split) {
        return splitConvertToIntArray(str, split, 0);
    }

    // ===============split 2 Long=======================

    public static List<Long> splitConvertToLongList(String str, String split, long defaultVal) {
        if (isEmpty(str)) {
            return new ArrayList<Long>();
        }
        String[] strArr = str.split(split);
        List<Long> list = new ArrayList<Long>(strArr.length);
        for (int i = 0; i < strArr.length; i++) {
            try {
                long parseLong = Long.parseLong(strArr[i]);
                list.add(parseLong);
            } catch (NumberFormatException e) {
                list.add(defaultVal);
                continue;
            }
        }
        return list;
    }

    public static List<Long> splitConvertToLongList(String str, String split) {
        return splitConvertToLongList(str, split, 0L);
    }

    public static long[] splitConvertToLongArray(String str, String split, long defaultVal) {
        if (isEmpty(str)) {
            return new long[0];
        }
        String[] strArr = str.split(split);
        long[] result = new long[strArr.length];
        for (int i = 0; i < strArr.length; i++) {
            try {
                result[i] = Long.parseLong(strArr[i]);
            } catch (NumberFormatException e) {
                result[i] = defaultVal;
                continue;
            }
        }
        return result;
    }

    public static long[] splitConvertToLongArray(String str, String split) {
        return splitConvertToLongArray(str, split, 0L);
    }

    // ===============split convert byte=======================

    public static List<Byte> splitConvertToByteList(String str, String split, byte defaultVal) {
        if (isEmpty(str)) {
            return new ArrayList<Byte>();
        }
        String[] strArr = str.split(split);
        List<Byte> list = new ArrayList<Byte>(strArr.length);
        for (int i = 0; i < strArr.length; i++) {
            try {
                byte parseByte = Byte.parseByte(strArr[i]);
                list.add(parseByte);
            } catch (NumberFormatException e) {
                list.add(defaultVal);
                continue;
            }
        }
        return list;
    }

    public static List<Byte> splitConvertToByteList(String str, String split) {
        return splitConvertToByteList(str, split, (byte) 0);
    }

    public static byte[] splitConvertToByteArray(String str, String split, byte defaultVal) {
        if (isEmpty(str)) {
            return new byte[0];
        }
        String[] strArr = str.split(split);
        byte[] result = new byte[strArr.length];
        for (int i = 0; i < strArr.length; i++) {
            try {
                result[i] = Byte.parseByte(strArr[i]);
            } catch (NumberFormatException e) {
                result[i] = defaultVal;
                continue;
            }
        }
        return result;
    }

    public static byte[] splitConvertToByteArray(String str, String split) {
        return splitConvertToByteArray(str, split, (byte) 0);
    }

    // ===============split convert double=======================

    public static List<Double> splitConvertToDoubleList(String str, String split, double defaultVal) {
        if (isEmpty(str)) {
            return new ArrayList<Double>();
        }
        String[] strArr = str.split(split);
        List<Double> list = new ArrayList<Double>(strArr.length);
        for (int i = 0; i < strArr.length; i++) {
            try {
                double parseByte = Double.parseDouble(strArr[i]);
                list.add(parseByte);
            } catch (NumberFormatException e) {
                list.add(defaultVal);
                continue;
            }
        }
        return list;
    }

    public static List<Double> splitConvertToDoubleList(String str, String split) {
        return splitConvertToDoubleList(str, split, 0);
    }

    public static double[] splitConvertToDoubleArray(String str, String split, double defaultVal) {
        if (isEmpty(str)) {
            return new double[0];
        }
        String[] strArr = str.split(split);
        double[] result = new double[strArr.length];
        for (int i = 0; i < strArr.length; i++) {
            try {
                result[i] = Double.parseDouble(strArr[i]);
            } catch (NumberFormatException e) {
                result[i] = defaultVal;
                continue;
            }
        }
        return result;
    }

    public static double[] splitConvertToDoubleArray(String str, String split) {
        return splitConvertToDoubleArray(str, split, 0);
    }

    // ===============split convert float=======================

    public static List<Float> splitConvertToFloatList(String str, String split, float defaultVal) {
        if (isEmpty(str)) {
            return new ArrayList<Float>();
        }
        String[] strArr = str.split(split);
        List<Float> list = new ArrayList<Float>(strArr.length);
        for (int i = 0; i < strArr.length; i++) {
            try {
                float parseByte = Float.parseFloat(strArr[i]);
                list.add(parseByte);
            } catch (NumberFormatException e) {
                list.add(defaultVal);
                continue;
            }
        }
        return list;
    }

    public static List<Float> splitConvertToFloatList(String str, String split) {
        return splitConvertToFloatList(str, split, 0f);
    }

    public static float[] splitConvertToFloatArray(String str, String split, float defaultVal) {
        if (isEmpty(str)) {
            return new float[0];
        }
        String[] strArr = str.split(split);
        float[] result = new float[strArr.length];
        for (int i = 0; i < strArr.length; i++) {
            try {
                result[i] = Float.parseFloat(strArr[i]);
            } catch (NumberFormatException e) {
                result[i] = defaultVal;
                continue;
            }
        }
        return result;
    }

    public static float[] splitConvertToFloatArray(String str, String split) {
        return splitConvertToFloatArray(str, split, 0f);
    }


    public static String upperCaseFirstChar(String str) {
        if (str != null && !str.isEmpty()) {
            char firstChar = str.charAt(0);
            if (Character.isUpperCase(firstChar)) {
                return str;
            } else {
                char[] values = str.toCharArray();
                values[0] = Character.toUpperCase(firstChar);
                return new String(values);
            }
        } else {
            return str;
        }
    }

    public static String replace(String content, int begin, int end, String newStr) {
        if (begin < content.length() && begin >= 0) {
            if (end <= content.length() && end >= 0) {
                if (begin > end) {
                    return content;
                } else {
                    StringBuilder starStr = new StringBuilder();

                    for (int i = begin; i < end; ++i) {
                        starStr.append(newStr);
                    }

                    return content.substring(0, begin) + starStr + content.substring(end);
                }
            } else {
                return content;
            }
        } else {
            return content;
        }
    }

    /**
     * 按数据库列长度安全截断
     *
     * 专治「把异常堆栈往 varchar(128) 里塞」这类问题：MySQL 严格模式下会直接抛
     * Data truncation: Data too long for column 'xxx'，
     * 而这类写入往往发生在异常处理分支或 finally 里，一旦再抛异常，原本要记录的失败原因就彻底丢了，
     * 排查时只看到状态停在中间态、却找不到任何线索。
     *
     * @param text      原文，可为 null
     * @param maxLength 列的最大长度
     * @return 不超过 maxLength 的字符串；入参为 null 时原样返回 null
     */
    public static String truncate(String text, int maxLength) {
        if (text == null || maxLength <= 0 || text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength);
    }

}