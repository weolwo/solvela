package solvela.member.util;

import java.util.regex.Pattern;

/**
 * 会员手机号的<b>规范化</b>与校验。
 *
 * <h3>为什么必须有这一步</h3>
 * {@code t_member.phone_hash} 是 HMAC-SHA256 的输出，而哈希对输入是<b>逐字节敏感</b>的：
 * <pre>
 *   "13800000000"      -> a3f1...
 *   "138 0000 0000"    -> 7c92...   （用户从通讯录粘贴过来的）
 *   "+8613800000000"   -> e04b...   （国际格式）
 *   "13800000000 "     -> 55da...   （手机输入法自动补的尾空格）
 * </pre>
 * 同一个人、四个摘要。后果不是「查询失败」这么轻 —— {@code uk_mbr_phone_hash}
 * 这条唯一约束<b>拦不住</b>它们，于是一个手机号注册出四个账号，
 * 而客服在后台按手机号搜只能搜到其中一个。
 *
 * <p>🔴 所以：<b>任何写入或查询 phone_hash 的地方，都必须先过这里</b>。
 * 注册、登录、找回密码、后台导入、按手机号搜人 —— 一处漏了就是一个重复账号。
 * 规范化刻意<b>不</b>放在 {@code PiiHasher} 里顺手做：那个类是通用摘要工具，
 * 邮箱、身份证的规范化规则和手机号完全不同，混在一起只会让每种都做得半吊子。
 *
 * <h3>规范形式</h3>
 * 11 位裸号码，不带国家码、不带任何分隔符：{@code 13800000000}。
 * 落库的密文（{@link solvela.crypto.PiiCipher}）存的也是这一形式 ——
 * 密文和摘要必须来自同一个字符串，否则「解密出来的号」和「能登录的号」会是两个东西。
 *
 * <p>⚠️ 只支持中国大陆号码。要做港澳台/海外时，规范形式得改成 E.164（{@code +8613800000000}），
 * 那是一次<b>存量数据重算</b>：所有 phone 密文解出来、按新规则规范化、重新加密并重算 hash。
 * 现在把规则收在这一个类里，就是为了那天只用改这里。
 *
 * @Date 2026-08-25
 */
public final class MemberPhoneUtil {

    /**
     * 中国大陆手机号：1 开头，第二位 3-9，共 11 位。
     *
     * <p>比 {@code SolvelaVerificationUtil.PHONE_REGEXP}（{@code ^1[0-9]{10}}）严一点，
     * 且带了结尾锚 {@code $} —— 那个regex 没有 {@code $}，"138000000009999" 也能匹配上，
     * 用来卡登录入口不够。第二位排除 0/1/2 是因为大陆没有这三个号段。
     */
    private static final Pattern CN_MOBILE = Pattern.compile("^1[3-9]\\d{9}$");

    /** 用户可能粘进来的分隔符：空格（含全角）、连字符、括号、点 */
    private static final Pattern SEPARATORS = Pattern.compile("[\\s\\u3000\\-()（）.]");

    private MemberPhoneUtil() {
    }

    /**
     * 规范化。输入非法（含 null）时返回 {@code null} —— 调用方必须判空，
     * 不要把结果直接丢给 {@code PiiHasher.hash}：那里 null 会返回 null 摘要，
     * 于是「按 NULL 查」，表现是「查无此人」而不是「手机号格式不对」，
     * 用户看到的提示会驴唇不对马嘴。
     */
    public static String normalize(String rawPhone) {
        if (rawPhone == null) {
            return null;
        }
        String s = SEPARATORS.matcher(rawPhone).replaceAll("");
        // 去国家码：+86 / 0086 / 86 三种写法都见过
        if (s.startsWith("+86")) {
            s = s.substring(3);
        } else if (s.startsWith("0086")) {
            s = s.substring(4);
        } else if (s.length() == 13 && s.startsWith("86")) {
            // 🔴 length==13 这个条件不能省。没有它，任何以 86 开头的串都会被砍掉两位，
            //    残缺输入 "8613800" 会变成 "13800" —— 校验能拦下来，但错误提示会指向
            //    一个用户根本没输入过的号码。加上长度判断，只有「86 + 11 位」才当国家码处理。
            s = s.substring(2);
        }
        return isValid(s) ? s : null;
    }

    /**
     * 校验<b>已规范化</b>的号码。传原始输入进来多半会误判，请先 {@link #normalize}。
     */
    public static boolean isValid(String normalizedPhone) {
        return normalizedPhone != null && CN_MOBILE.matcher(normalizedPhone).matches();
    }

    /**
     * 脱敏展示：{@code 138****0000}。用于「我的」页面、客服工单这类需要让本人确认
     * 「是不是这个号」但不必给出完整号码的场景。
     *
     * <p>非法输入原样返回 —— 这里的职责是展示，不是校验，为一个显示问题抛异常不值当。
     */
    public static String mask(String normalizedPhone) {
        if (!isValid(normalizedPhone)) {
            return normalizedPhone;
        }
        return normalizedPhone.substring(0, 3) + "****" + normalizedPhone.substring(7);
    }
}
