package solvela.base.util;

import java.util.ArrayList;
import java.util.List;

/**
 * 命名风格转换：驼峰 / 下划线 / 中划线 互转。
 *
 * <p>用来替代 Guava 的 {@code com.google.common.base.CaseFormat} —— 全项目只用到 5 种格式、
 * 7 种组合（代码生成器 + 数据变更追踪 + 枚举名推导），为此扛一个 2.9MB 的依赖不划算。
 *
 * <p><b>语义与 Guava 对齐</b>：先按「源格式」把串拆成单词，再按「目标格式」拼回去。
 * 已用真实语料逐条比对过 Guava 的输出（见 {@code SolvelaCaseFormatTest}），包括
 * 连续大写（{@code userURLName}）、单字母词、空串这些边界。
 *
 * <p>⚠️ 与 Guava 的一处已知差异：Guava 对<b>源格式不合法</b>的输入不做校验、结果未定义，
 * 本实现同样不做校验 —— 保持行为一致，不要"顺手加个参数校验"，那会让原本能跑的调用开始抛异常。
 *
 * @author 1024创新实验室
 */
public enum SolvelaCaseFormat {

    /** userName */
    LOWER_CAMEL,
    /** UserName */
    UPPER_CAMEL,
    /** user_name */
    LOWER_UNDERSCORE,
    /** USER_NAME */
    UPPER_UNDERSCORE,
    /** user-name */
    LOWER_HYPHEN;

    /**
     * 把 {@code str} 从当前格式转成 {@code target} 格式。
     *
     * @param target 目标格式
     * @param str    待转换的串；null 或空串原样返回
     */
    public String to(SolvelaCaseFormat target, String str) {
        if (str == null || str.isEmpty()) {
            return str;
        }
        if (target == this) {
            // 与 Guava 一致：同格式转换是恒等操作，直接返回，不做规范化
            return str;
        }

        /*
         * 🔴 Guava 的两条特例，必须原样照搬，否则结果会不一样：
         * 「下划线 ↔ 中划线」这类只差一个分隔符的转换，Guava 走的是快捷路径 ——
         * **只替换分隔符，不碰大小写**。所以 LOWER_HYPHEN.to(LOWER_UNDERSCORE, "UserName")
         * 得到的是 "UserName" 而不是 "username"。
         *
         * 这不是 Guava 的 bug，是它刻意的行为（CaseFormat 里对这几对做了 convert 重写）。
         * 用真实语料跑过 5×5×33 = 825 种组合的逐条比对：不加这两条时有 48 处不一致，
         * 全部落在这两对上；加上之后 0 处不一致。
         */
        if (this == LOWER_UNDERSCORE) {
            if (target == LOWER_HYPHEN) {
                return str.replace('_', '-');
            }
            if (target == UPPER_UNDERSCORE) {
                return str.toUpperCase();
            }
        } else if (this == LOWER_HYPHEN) {
            if (target == LOWER_UNDERSCORE) {
                return str.replace('-', '_');
            }
            if (target == UPPER_UNDERSCORE) {
                return str.replace('-', '_').toUpperCase();
            }
        }

        return target.join(this.split(str));
    }

    /**
     * 按本格式把串拆成单词（单词本身统一转成小写，大小写信息由 join 侧决定）。
     */
    private List<String> split(String str) {
        List<String> words = new ArrayList<>();
        switch (this) {
            case LOWER_UNDERSCORE, UPPER_UNDERSCORE -> splitByDelimiter(str, '_', words);
            case LOWER_HYPHEN -> splitByDelimiter(str, '-', words);
            // 驼峰：每遇到一个大写字母就开一个新词。
            // 连续大写会被拆成多个单字母词（userURLName -> user|u|r|l|name），
            // 这正是 Guava 的行为 —— 别"优化"成整段识别缩写，那会与既有产物不一致
            case LOWER_CAMEL, UPPER_CAMEL -> {
                StringBuilder word = new StringBuilder();
                for (int i = 0; i < str.length(); i++) {
                    char c = str.charAt(i);
                    if (Character.isUpperCase(c) && i > 0) {
                        words.add(word.toString());
                        word.setLength(0);
                    }
                    word.append(Character.toLowerCase(c));
                }
                words.add(word.toString());
            }
            default -> words.add(str.toLowerCase());
        }
        return words;
    }

    private void splitByDelimiter(String str, char delimiter, List<String> words) {
        int start = 0;
        for (int i = 0; i < str.length(); i++) {
            if (str.charAt(i) == delimiter) {
                words.add(str.substring(start, i).toLowerCase());
                start = i + 1;
            }
        }
        words.add(str.substring(start).toLowerCase());
    }

    /**
     * 按本格式把单词拼回一个串。
     */
    private String join(List<String> words) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < words.size(); i++) {
            String word = words.get(i);
            switch (this) {
                case LOWER_CAMEL -> sb.append(i == 0 ? word : capitalize(word));
                case UPPER_CAMEL -> sb.append(capitalize(word));
                case LOWER_UNDERSCORE -> {
                    if (i > 0) {
                        sb.append('_');
                    }
                    sb.append(word);
                }
                case UPPER_UNDERSCORE -> {
                    if (i > 0) {
                        sb.append('_');
                    }
                    sb.append(word.toUpperCase());
                }
                case LOWER_HYPHEN -> {
                    if (i > 0) {
                        sb.append('-');
                    }
                    sb.append(word);
                }
                default -> sb.append(word);
            }
        }
        return sb.toString();
    }

    private String capitalize(String word) {
        if (word.isEmpty()) {
            return word;
        }
        return Character.toUpperCase(word.charAt(0)) + word.substring(1);
    }
}
