package solvela.member.api;

/**
 * 会员密码强度规则的<b>唯一定义处</b>。
 *
 * <h3>🔴 为什么在【契约模块】而不是会员域里</h3>
 * 规则由会员域<b>执行</b>（{@code MemberRegisterService} 调 {@link #isValid}），
 * 但提示文案要由<b>网关</b>说给用户听（{@link RegisterFailReason#WEAK_PASSWORD} 回来时展示 {@link #HINT}）。
 * 而网关的 classpath 上<b>没有也不能有</b>任何域实现模块 —— 那是 {@code AppBoundaryTest}
 * 守着的硬边界。规则放在域里，网关就只能自己再抄一份文案，于是又变成两份。
 *
 * <p>本类是零依赖的纯逻辑，进 api 模块不给任何调用方带来包袱。
 * ⚠️ 别照这个先例往 api 里塞业务逻辑：能进来的标准是「<b>调用方也必须知道这条规则</b>」，
 * 而不是「反正它没依赖」。
 *
 * <h3>为什么只有一处</h3>
 * 与 {@code MemberPhoneUtil} 同一个道理：规则写两遍（前端一遍、后端一遍）迟早对不上，
 * 而对不上的表现是「前端说可以、提交上去被拒」，用户完全不知道该怎么改。
 *
 * <p>🔴 前端<b>只展示提示文案</b>（「8-32 位，需包含字母和数字」），不实现校验。
 * 想让前端做即时反馈时，正确做法是把提示文案也从这里下发，而不是照着抄一条正则。
 *
 * <h3>规则本身刻意保守</h3>
 * 长度 8~32、至少包含字母与数字各一。<b>不强制特殊字符、不禁止连续字符</b> ——
 * 那类规则对暴力破解的贡献极小，却大幅推高「用户记不住于是写在便利贴上」的概率。
 * 真正扛暴力破解的是 Argon2id（见 {@code PasswordCipher}）与登录失败限频
 * （{@code MemberOperationLimitService}），不是密码里有没有感叹号。
 *
 * <p>上限 32 不是安全需要，是防呆：Argon2 对超长输入照样能算，
 * 但一个 500 字符的「密码」几乎总是粘贴事故。
 */
public final class MemberPasswordPolicy {

    public static final int MIN_LENGTH = 8;

    public static final int MAX_LENGTH = 32;

    /** 给前端展示的提示文案。跟着规则走，改规则时这句话必须一起改 */
    public static final String HINT = "密码 8-32 位，需同时包含字母和数字";

    private MemberPasswordPolicy() {
    }

    /**
     * 校验明文密码。
     *
     * <p>不返回「哪一条不满足」：逐条告诉用户「缺数字」「太短」看似友好，
     * 实际是把规则一条条念给攻击者听。给一句完整的 {@link #HINT} 更有用也更省事。
     */
    public static boolean isValid(String rawPassword) {
        if (rawPassword == null || rawPassword.length() < MIN_LENGTH || rawPassword.length() > MAX_LENGTH) {
            return false;
        }
        boolean hasLetter = false;
        boolean hasDigit = false;
        for (int i = 0; i < rawPassword.length(); i++) {
            char c = rawPassword.charAt(i);
            if (Character.isLetter(c)) {
                hasLetter = true;
            } else if (Character.isDigit(c)) {
                hasDigit = true;
            }
            if (hasLetter && hasDigit) {
                return true;
            }
        }
        return false;
    }
}
