package solvela.crypto;

import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;

/**
 * 登录密码的哈希与校验：Argon2id，参数取 Spring Security 5.8 的默认档。
 *
 * <h3>为什么不放在等保那一包里</h3>
 * 这两个方法此前是 {@code SecurityPasswordService} 的静态成员，和「密码复杂度」「多久必须改密」
 * 「不许与前 N 次重复」挤在一起。但它们的性质完全不同：<b>后者是可以被后台开关改掉的策略，
 * 前者是不能改的算法</b>——把明文换成哈希、把哈希拿回来比对，任何有账号体系的模块都要用，
 * 与等保开不开无关。混在一起的直接代价是：会员端（solvela-app）为了校验一次密码，
 * 得依赖整个三级等保服务，连带把 {@code t_login_fail}、{@code t_config} 的等保配置一起拖进来。
 *
 * <p>所以算法下沉到 {@code common/crypto}（与 {@link PiiCipher}、{@link PiiHasher} 同层），
 * 策略留在等保服务里。
 *
 * <h3>🔴 换算法等于让所有人无法登录</h3>
 * {@link Argon2PasswordEncoder#matches} 只认自己这套参数产出的串。改动这里的编码器或参数，
 * 库里已有的 {@code t_employee.login_pwd} / {@code t_member.password} 全部校验失败，
 * 且<b>没有任何报错</b>——表现是「所有人密码都不对」。真要换，得走「双读一写」：
 * 先兼容校验新旧两种格式，登录成功时顺手重写成新格式，等存量清空再删掉旧分支。
 */
public final class PasswordCipher {

    private static final Argon2PasswordEncoder ENCODER = Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8();

    private PasswordCipher() {
    }

    /**
     * 明文密码 -> 存库用的哈希串（含盐与参数，自带随机盐，同一密码每次结果不同）
     */
    public static String encode(String rawPassword) {
        return ENCODER.encode(rawPassword);
    }

    /**
     * 校验明文密码与库里的哈希串是否匹配
     */
    public static boolean matches(String rawPassword, String encodedPassword) {
        return ENCODER.matches(rawPassword, encodedPassword);
    }
}
