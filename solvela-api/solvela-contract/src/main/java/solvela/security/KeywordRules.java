package solvela.security;

import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * 关键字拦截的规则（网关方案 §5.4）—— 网关执行、后台保存时校验，<b>两边用这同一份</b>编译与反例集。
 *
 * <h3>它不是防注入的主防线</h3>
 * 代码层全是参数化查询，注入本来就没有口子。这一层挡的是「来探测的人」：参数里带着 {@code sleep(5)} 的请求一看就不是正常用户，
 * 在网关最前面挡掉，它就不会走到会话查询、限流计数、下游、数据库 —— 越往后每个请求越贵。
 *
 * <h3>🔴 规则一旦误伤，代价是把真人封 24 小时</h3>
 * 所以：
 * <ul>
 *   <li>不用裸子串，用带词边界的正则 —— 裸子串 {@code or(} 会命中 {@code color(}、{@code floor(}；</li>
 *   <li>每条规则编译时都要过一遍 {@link #NORMAL_INPUTS}（{@code UA} 规则过 {@link #NORMAL_USER_AGENTS}），
 *       任何一条命中就拒绝 —— 后台存不进去，网关加载时也丢掉；</li>
 *   <li>密码、验证码一类字段<b>不参与匹配</b>（{@link #isSecretField}）：密码里带括号很常见，
 *       用正确的密码登录换来封一天，这个人永远想不明白为什么；</li>
 *   <li>匹配有时间上限（{@link #find}）：规则是后台填的正则，一条写坏的（{@code (a+)+$} 这种）能把网关的线程卡死。</li>
 * </ul>
 *
 * @Date 2026-10-17
 */
public final class KeywordRules {

    private KeywordRules() {
    }

    /** JSON 请求体超过这么大就不扫：成本不成比例 */
    public static final int MAX_BODY_BYTES = 8192;

    /** 单次匹配的时间上限 */
    public static final long MATCH_BUDGET_NANOS = 20_000_000L;

    /** 编码进日志与事件（{@code t_security_incident.rule_code} 宽 32） */
    private static final Pattern CODE = Pattern.compile("[A-Z0-9_]{1,32}");

    /** 规则作用在哪：请求参数（路径、查询串、JSON 请求体的值），或 {@code User-Agent} */
    public enum Scope { PARAM, UA }

    /** 一条编译好的规则 */
    public record Rule(String code, Scope scope, Pattern pattern) {
    }

    /** 一次命中：哪条规则、在哪（路径 / 查询串 / 请求体 / UA）、命中的那一段（截断到 64 字符） */
    public record Hit(String code, String location, String sample) {
    }

    /**
     * 正常用户真的会输入的东西。任何一条规则命中其中任何一条，这条规则就不许上线。
     * 中英文商品名、带括号的昵称、地址、长得像符号密码的字符串、普通的英文句子都要有。
     */
    public static final List<String> NORMAL_INPUTS = List.of(
            "Floor(落地灯)", "Doctor(医生)", "Color(2024)!", "XX路(东门)", "解放路100号(东门)3楼",
            "iPhone 15 Pro (256GB)", "Sleep(睡眠)眼罩", "sleep (8h) 好梦套装", "Tea or (coffee)", "Rock and Roll (Live)",
            "喜欢(猫)和(狗)", "error(404) 页面", "for(int i = 0; i < n; i++)", "O'Neil", "I'm here or there",
            "1+1=2", "a=b", "x = 1 or 2", "price<100", "<b>加粗</b>", "../readme.md", "C:\\Users\\me",
            "JavaScript: The Good Parts", "Union Square 联合广场", "select 精选好物", "Benchmark (2020)",
            "Wait for delay 等待", "P@ssw0rd!", "a'b\"c", "#hashtag", "50% off!", "user@example.com",
            "https://example.com/path?q=1&b=2", "「双十一」限时 5 折", "Mr. Information", "or", "and", "union",
            // 用户乱按键盘：顺带把嵌套量词（(a+)+$ 这种）在保存时就暴露出来 —— 它们在这种输入上会跑到超时
            "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa!", "1111111111111111111111111111111111111111x");

    /** 正常的 User-Agent：浏览器、App、常见的 HTTP 库。🔴 curl / Postman 不是扫描器，不该被封 */
    public static final List<String> NORMAL_USER_AGENTS = List.of(
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0 Safari/537.36",
            "Mozilla/5.0 (iPhone; CPU iPhone OS 18_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Mobile/15E148 MicroMessenger/8.0",
            "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0 Mobile Safari/537.36",
            "okhttp/4.12.0", "curl/8.5.0", "PostmanRuntime/7.42.0", "Dart/3.5 (dart:io)", "Java-http-client/21");

    /**
     * 编译一条规则并过反例集。
     *
     * @throws IllegalArgumentException 编码不合法、正则写不对、命中了反例、或在反例上跑太慢 —— 消息里写着是哪一条
     */
    public static Rule compile(String code, String scope, String regex) {
        if (code == null || !CODE.matcher(code).matches()) {
            throw new IllegalArgumentException("关键字规则编码只能是 1~32 位大写字母、数字、下划线: " + code);
        }
        Scope s;
        try {
            s = Scope.valueOf(scope == null ? "PARAM" : scope.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("关键字规则 " + code + " 的 scope 只能是 PARAM 或 UA: " + scope);
        }
        if (regex == null || regex.isBlank()) {
            throw new IllegalArgumentException("关键字规则 " + code + " 没有正则");
        }
        Pattern pattern;
        try {
            pattern = Pattern.compile(regex, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
        } catch (PatternSyntaxException e) {
            throw new IllegalArgumentException("关键字规则 " + code + " 的正则写不对: " + e.getDescription());
        }
        for (String normal : s == Scope.UA ? NORMAL_USER_AGENTS : NORMAL_INPUTS) {
            // 超时再试一次：一次 GC 停顿不该让一条好规则存不进去
            Boolean found = findWithin(pattern, normal);
            if (found == null) {
                found = findWithin(pattern, normal);
            }
            if (found == null) {
                throw new IllegalArgumentException("关键字规则 " + code + " 在正常输入「" + normal + "」上跑得太慢，"
                        + "多半是嵌套的量词（(a+)+ 这种），会卡住网关");
            }
            if (found) {
                throw new IllegalArgumentException("关键字规则 " + code + " 命中了正常输入「" + normal
                        + "」—— 上线的话这样的用户会被封 24 小时。改写正则（加词边界、收紧上下文）");
            }
        }
        return new Rule(code, s, pattern);
    }

    /** 在时间上限内找一次；超时返回 null */
    private static Boolean findWithin(Pattern pattern, String value) {
        try {
            return pattern.matcher(new Budgeted(value, System.nanoTime() + MATCH_BUDGET_NANOS)).find();
        } catch (Budgeted.TooSlow e) {
            return null;
        }
    }

    /**
     * 在 {@code value} 里找第一条命中的规则（只看 {@code scope} 那一类）。超时的规则按没命中处理。
     *
     * @return 没命中为 null
     */
    public static Hit find(List<Rule> rules, Scope scope, String value, String location) {
        if (value == null || value.isEmpty()) {
            return null;
        }
        for (Rule rule : rules) {
            if (rule.scope() != scope) {
                continue;
            }
            try {
                Matcher m = rule.pattern().matcher(new Budgeted(value, System.nanoTime() + MATCH_BUDGET_NANOS));
                if (m.find()) {
                    String matched = value.substring(m.start(), m.end());
                    return new Hit(rule.code(), location, matched.length() > 64 ? matched.substring(0, 64) : matched);
                }
            } catch (Budgeted.TooSlow e) {
                // 一条规则在这个输入上跑超时：放过这一条，不让整个请求卡在这里
            }
        }
        return null;
    }

    /**
     * 密码、验证码一类字段：不参与匹配，命中的片段也绝不落到日志与库里。
     * 宁可多排除：一个被排除的字段只是少扫一处，误扫一个密码字段是把真人封一天。
     */
    public static boolean isSecretField(String name) {
        if (name == null) {
            return false;
        }
        String n = name.toLowerCase(Locale.ROOT);
        return n.contains("password") || n.contains("passwd") || n.contains("pwd") || n.contains("credential")
                || n.contains("secret") || n.contains("token") || n.equals("code") || n.endsWith("code")
                || n.contains("captcha") || n.equals("otp");
    }

    /**
     * 给正则一个时间上限：{@link Matcher} 每读一个字符都经过 {@link #charAt}，超了就抛出来。
     * 不需要额外的线程，代价是每个字符一次时钟比较。
     */
    private record Budgeted(CharSequence inner, long deadline) implements CharSequence {

        static final class TooSlow extends RuntimeException {
            TooSlow() {
                super(null, null, false, false);
            }
        }

        @Override
        public char charAt(int index) {
            if (System.nanoTime() > deadline) {
                throw new TooSlow();
            }
            return inner.charAt(index);
        }

        @Override
        public int length() {
            return inner.length();
        }

        @Override
        public CharSequence subSequence(int start, int end) {
            return new Budgeted(inner.subSequence(start, end), deadline);
        }

        @Override
        public String toString() {
            return inner.toString();
        }
    }
}
