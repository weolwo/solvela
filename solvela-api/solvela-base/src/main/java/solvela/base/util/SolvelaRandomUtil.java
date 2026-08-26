package solvela.base.util;

import java.security.SecureRandom;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 随机工具类
 *
 * 移除 hutool 后接管 RandomUtil / IdUtil 的用量。区分两类随机源，别混用：
 * <ul>
 *     <li>{@link ThreadLocalRandom} —— 默认。快，无锁，但**可预测**，只能用于测试数据、
 *         打散步长这类「猜到了也没损失」的场景。</li>
 *     <li>{@link #secureRandom()} —— 密码学安全。凡是验证码、令牌、密码、
 *         任何「猜到就能冒用」的东西必须走它。</li>
 * </ul>
 *
 * @Date 2026-08-08
 */
public class SolvelaRandomUtil {

    /**
     * 数字字符集
     */
    private static final String NUMBER_CHARS = "0123456789";

    /**
     * 小写字母 + 数字，与 hutool RandomUtil.randomString 的默认字符集一致
     */
    private static final String BASE_CHAR_NUMBER = "abcdefghijklmnopqrstuvwxyz" + NUMBER_CHARS;

    /**
     * SecureRandom 实例化开销不小（要向系统熵源要种子），而它本身是线程安全的，
     * 所以持有一个共享实例，不要在循环里 new。
     */
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private SolvelaRandomUtil() {
    }

    /**
     * 密码学安全的随机源，线程安全，可直接复用
     */
    public static SecureRandom secureRandom() {
        return SECURE_RANDOM;
    }

    /**
     * 随机 int，区间 [min, max)
     */
    public static int randomInt(int min, int max) {
        return ThreadLocalRandom.current().nextInt(min, max);
    }

    /**
     * 随机 long，区间 [min, max)
     */
    public static long randomLong(long min, long max) {
        return ThreadLocalRandom.current().nextLong(min, max);
    }

    public static boolean randomBoolean() {
        return ThreadLocalRandom.current().nextBoolean();
    }

    /**
     * 指定长度的纯数字串（可能以 0 开头，所以返回 String 而不是数字）
     */
    public static String randomNumbers(int length) {
        return randomString(NUMBER_CHARS, length);
    }

    /**
     * 指定长度的随机串，字符集为小写字母 + 数字
     */
    public static String randomString(int length) {
        return randomString(BASE_CHAR_NUMBER, length);
    }

    /**
     * 在给定字符集里随机取 length 个字符（可重复）
     */
    public static String randomString(String charset, int length) {
        if (length < 1) {
            return "";
        }
        ThreadLocalRandom random = ThreadLocalRandom.current();
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(charset.charAt(random.nextInt(charset.length())));
        }
        return sb.toString();
    }

    /**
     * 密码学安全的随机串：验证码、临时令牌这类「猜中即可冒用」的场景用它，别用 {@link #randomString}
     */
    public static String secureRandomString(String charset, int length) {
        if (length < 1) {
            return "";
        }
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(charset.charAt(SECURE_RANDOM.nextInt(charset.length())));
        }
        return sb.toString();
    }

    /**
     * 密码学安全的纯数字串
     */
    public static String secureRandomNumbers(int length) {
        return secureRandomString(NUMBER_CHARS, length);
    }

    /**
     * 去掉中划线的 UUID，32 位十六进制小写（原 IdUtil.fastSimpleUUID / UUID.randomUUID(true).toString(true)）
     */
    public static String simpleUuid() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
