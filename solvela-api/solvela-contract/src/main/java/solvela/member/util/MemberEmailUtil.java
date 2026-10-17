package solvela.member.util;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * 会员邮箱的<b>规范化</b>与校验。
 *
 * <h3>为什么必须有这一步</h3>
 * 与 {@link MemberPhoneUtil} 同一个理由：{@code t_member.email_hash} 是 HMAC-SHA256 的输出，
 * 而哈希对输入<b>逐字节敏感</b>。邮箱比手机号更容易写出多种形式：
 * <pre>
 *   "a@Example.com"    -> 3f1a...   （域名大写）
 *   "A@example.com"    -> 9c02...   （本地部分大写）
 *   " a@example.com "  -> 55da...   （从别处粘贴带的空格）
 * </pre>
 * {@code uk_mbr_email_hash} 这条唯一约束<b>拦不住</b>它们，于是一个邮箱注册出多个账号。
 *
 * <p>🔴 所以：<b>任何写入或查询 email_hash 的地方都必须先过这里</b> ——
 * 注册、登录、绑定、重置密码、后台按邮箱搜人，一处漏了就是一个重复账号。
 *
 * <h3>🔴 只把域名转小写，本地部分【原样保留】</h3>
 * RFC 5321 说得很清楚：域名部分大小写不敏感，而 {@code @} 左边的本地部分
 * <b>是否敏感由收件服务器决定</b>。Gmail 不敏感，但不是所有服务器都这样。
 *
 * <p>全部转小写会把 {@code Bob@corp.com} 和 {@code bob@corp.com} 判成同一个人 ——
 * 在少数服务器上那是两个不同的信箱，后果是<b>把验证码发给了别人</b>。
 * 反过来只留一半规范化的代价是「同一个人可能注册出两个账号」，
 * 那是个体验问题；前者是安全问题。两害相权。
 *
 * <h3>不做的事</h3>
 * 刻意<b>不</b>做 Gmail 的 {@code .} 与 {@code +tag} 归一化。那是 Gmail 的私有规则，
 * 对别家不成立；按它归一化等于替所有邮件服务商做了一个它们没答应过的假设。
 * 「用 {@code a+1@gmail.com} 多注册几个号」要防的话，属于风控维度，不该混进规范化。
 *
 * @Date 2026-09-09
 */
public final class MemberEmailUtil {

    /**
     * 邮箱格式。刻意<b>不追求完整实现 RFC 5322</b> —— 那个语法允许引号、注释、
     * 嵌套括号，完整的正则有几千个字符，而它多认出来的那些地址现实里不存在，
     * 却会放进一堆能通过校验、实际发不出去的串。
     *
     * <p>这里认的是「一个 {@code @}，两侧都不为空，域名至少有一个点且顶级域 2 位以上」——
     * 覆盖真实世界的绝大多数，拒绝掉明显的垃圾。<b>真正的校验是那封信收不收得到</b>，
     * 而验证码这件事本身就是在做这个校验。
     */
    private static final Pattern EMAIL = Pattern.compile(
            "^[A-Za-z0-9._%+\\-]{1,64}@[A-Za-z0-9.\\-]{1,190}\\.[A-Za-z]{2,}$");

    /** {@code t_member.email} 是 varchar(255) 存密文，明文留足余量。 */
    private static final int MAX_LENGTH = 254;

    private MemberEmailUtil() {
    }

    /**
     * 规范化：去首尾空白 → 域名转小写 → 校验。非法（含 null）返回 {@code null}。
     *
     * <p>调用方<b>必须判空</b>，不要把结果直接丢给 {@code PiiHasher.hash}：
     * 那里 null 会返回 null 摘要，于是变成「按 NULL 查」，
     * 表现是「查无此人」而不是「邮箱格式不对」，提示会驴唇不对马嘴。
     * 这条与 {@link MemberPhoneUtil#normalize} 完全一致。
     */
    public static String normalize(String rawEmail) {
        if (rawEmail == null) {
            return null;
        }
        String s = rawEmail.strip();
        if (s.length() > MAX_LENGTH) {
            return null;
        }
        int at = s.lastIndexOf('@');
        if (at <= 0 || at == s.length() - 1) {
            return null;
        }
        // 只动域名。本地部分原样保留 —— 理由见类注释，这一行是个安全决定不是风格决定
        s = s.substring(0, at) + "@" + s.substring(at + 1).toLowerCase(Locale.ROOT);
        return EMAIL.matcher(s).matches() ? s : null;
    }

    /** 是否是一个合法邮箱（已规范化的形式）。 */
    public static boolean isValid(String normalizedEmail) {
        return normalizedEmail != null && normalizedEmail.length() <= MAX_LENGTH
                && EMAIL.matcher(normalizedEmail).matches();
    }

    /**
     * 打码，给日志与展示用：{@code ab***@example.com}。
     *
     * <p>🔴 日志里<b>不要打印完整邮箱</b>。它既是登录凭据的一半，也是个人信息 ——
     * 而验证码相关的日志恰恰是最容易被大量打印、最容易被采集到 ELK 的那一类。
     */
    public static String mask(String normalizedEmail) {
        if (normalizedEmail == null) {
            return null;
        }
        int at = normalizedEmail.lastIndexOf('@');
        if (at <= 0) {
            return "***";
        }
        String local = normalizedEmail.substring(0, at);
        String visible = local.length() <= 2 ? local.substring(0, 1) : local.substring(0, 2);
        return visible + "***" + normalizedEmail.substring(at);
    }
}
