package sa.base.common.util;

import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.function.Predicate;
import java.util.function.Supplier;

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
     * 业务前缀：编码首位放一个字母，一眼看出它属于哪种业务。
     *
     * <h3>为什么是「1 位前缀 + 9 位随机」而不是「前缀 + 完整 10 位」</h3>
     * 总长仍是 10，{@link #BIZ_CODE_REGEX} 一个字都不用改 ——
     * 前后端所有 @Pattern、regular.bizCode 以及唯一索引全都不受影响，
     * <b>存量数据也不需要迁移</b>（老编码没有前缀，但依然合法）。
     *
     * <h3>⚠️ 前缀只在「生成」时加，绝不参与校验</h3>
     * 存量编码没有前缀，一旦把前缀写进 @Pattern，老活动一打开编辑就会被判非法；
     * 运营手输的编码也不该被拦。
     * <b>前缀是给人看的线索，不是给代码依赖的契约</b> ——
     * 任何「按前缀判断业务类型」的写法都会立刻把它变成必须迁移存量的硬约束，不要那样做。
     *
     * <h3>关于碰撞概率</h3>
     * 随机位由 10 降到 9，空间从 36^10≈3.7e15 缩到 36^9≈1.0e14（小了 36 倍）。
     * 在本系统的数据量级下仍然可忽略，且 {@link #generateUniqueBizCode} 本就带判重重试。
     */
    public static final class BizCodePrefix {

        private BizCodePrefix() {
        }

        /** 活动 Activity */
        public static final char ACTIVITY = 'A';
        /** 奖品 Prize */
        public static final char PRIZE = 'P';
        /** 奖池 Draw pool */
        public static final char POOL = 'D';
        /** 任务模板 Task template */
        public static final char TASK_TEMPLATE = 'T';
        /** 彩票玩法 Lottery */
        public static final char LOTTERY = 'L';
        /** 商城商品 Mall commodity */
        public static final char MALL_COMMODITY = 'M';
        /** 商城 SKU */
        public static final char MALL_SKU = 'S';
    }

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
     * 生成一个带业务前缀的编码：首位是前缀字母，其余 9 位随机，总长仍为 {@link #BIZ_CODE_LENGTH}
     *
     * @param prefix 业务前缀，取值见 {@link BizCodePrefix}
     */
    public static String generateBizCode(char prefix) {
        StringBuilder builder = new StringBuilder(BIZ_CODE_LENGTH);
        builder.append(prefix);
        for (int i = 1; i < BIZ_CODE_LENGTH; i++) {
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
        return generateUnique(SmartCodeUtil::generateBizCode, existsChecker);
    }

    /**
     * 生成一个「库里不存在」的带业务前缀编码
     *
     * @param prefix        业务前缀，取值见 {@link BizCodePrefix}
     * @param existsChecker 判重函数：编码已被占用返回 true
     */
    public static String generateUniqueBizCode(char prefix, Predicate<String> existsChecker) {
        return generateUnique(() -> generateBizCode(prefix), existsChecker);
    }

    private static String generateUnique(Supplier<String> generator, Predicate<String> existsChecker) {
        for (int i = 0; i < MAX_RETRY; i++) {
            String code = generator.get();
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

    /**
     * 单号随机段长度。前缀(≤4) + 日期(8) + 随机(16) = 28，控制在 varchar(32) 内
     */
    private static final int TRADE_NO_RANDOM_LENGTH = 16;

    private static final DateTimeFormatter TRADE_NO_DATE = DateTimeFormatter.ofPattern("yyyyMMdd");

    /**
     * 生成对外业务单号：前缀 + yyyyMMdd + 16位随机
     *
     * 与 {@link #generateBizCode()} 的定位不同：
     * 业务编码是「配置的身份」，运营手输手选，所以要短；
     * 单号是「一次交易的凭证」，客服/财务对账要用，所以带日期前缀便于人工定位与分库分表，
     * 且不需要人来输入，可以长一些换取更低的碰撞概率。
     *
     * @param prefix 业务前缀，如 PRP(提案)，建议不超过 4 个字符
     */
    public static String generateTradeNo(String prefix) {
        StringBuilder builder = new StringBuilder(32);
        builder.append(prefix == null ? "" : prefix);
        builder.append(LocalDate.now().format(TRADE_NO_DATE));
        for (int i = 0; i < TRADE_NO_RANDOM_LENGTH; i++) {
            builder.append(ALPHABET[RANDOM.nextInt(ALPHABET.length)]);
        }
        return builder.toString();
    }
}
