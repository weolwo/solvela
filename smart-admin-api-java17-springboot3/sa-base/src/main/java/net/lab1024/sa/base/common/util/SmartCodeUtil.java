package net.lab1024.sa.base.common.util;

import java.security.SecureRandom;
import java.util.function.Predicate;

/**
 * 业务编码工具
 *
 * 统一约定：活动编码、奖品编码等对外业务编码一律为「10 位大写字母 + 数字」的随机组合（如 H88JHKJFNE），全局唯一。
 * 运营既可以手工输入，也可以点按钮生成；无论哪条路径，服务端都要按 {@link #BIZ_CODE_REGEX} 校验并做唯一性判重。
 *
 * @Author alaric
 * @Date 2026-07-26
 */
public class SmartCodeUtil {

    private SmartCodeUtil() {
    }

    /**
     * 业务编码长度
     */
    public static final int BIZ_CODE_LENGTH = 10;

    /**
     * 业务编码格式：10 位大写字母或数字。
     * 注意：作为注解参数使用（@Pattern），必须保持为编译期常量
     */
    public static final String BIZ_CODE_REGEX = "^[A-Z0-9]{10}$";

    /**
     * 校验不通过时的统一文案
     */
    public static final String BIZ_CODE_MESSAGE = "编码必须是 10 位大写字母或数字的组合，如 H88JHKJFNE";

    private static final char[] ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789".toCharArray();

    /**
     * 唯一性重试上限：36^10 的空间下撞满 10 次基本等同于「判重逻辑写错了」，此时应当报错而不是死循环
     */
    private static final int MAX_RETRY = 10;

    private static final SecureRandom RANDOM = new SecureRandom();

    /**
     * 生成一个业务编码（不保证唯一，唯一性交给 {@link #generateUniqueBizCode}）
     */
    public static String generateBizCode() {
        StringBuilder builder = new StringBuilder(BIZ_CODE_LENGTH);
        for (int i = 0; i < BIZ_CODE_LENGTH; i++) {
            builder.append(ALPHABET[RANDOM.nextInt(ALPHABET.length)]);
        }
        return builder.toString();
    }

    /**
     * 生成一个「库里不存在」的业务编码
     *
     * @param existsChecker 判重函数：编码已被占用返回 true
     */
    public static String generateUniqueBizCode(Predicate<String> existsChecker) {
        for (int i = 0; i < MAX_RETRY; i++) {
            String code = generateBizCode();
            if (!existsChecker.test(code)) {
                return code;
            }
        }
        throw new IllegalStateException("业务编码生成失败：连续 " + MAX_RETRY + " 次重复，请检查判重逻辑");
    }

    /**
     * 校验编码格式是否合法
     */
    public static boolean isValidBizCode(String code) {
        return code != null && code.matches(BIZ_CODE_REGEX);
    }
}
