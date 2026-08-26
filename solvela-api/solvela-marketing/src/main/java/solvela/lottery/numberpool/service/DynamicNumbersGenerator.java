package solvela.lottery.numberpool.service;

import solvela.base.util.SolvelaRandomUtil;
import lombok.extern.slf4j.Slf4j;
import solvela.base.exception.BusinessException;

import java.util.*;

@Slf4j
public class DynamicNumbersGenerator {

    /**
     * 动态彩票生成器
     *
     * @param charset       运营配置的可用字符集 (例如: "01258", 或者 "168")
     * @param numbersLength 号码长度 (例如: 5)
     * @param count         需要生成的数量 (例如: 10000)
     * @return 绝对不重复的随机号码集合
     */
    public static List<String> generateNumbers(String charset, int numbersLength, int count) {
        // 1. 获取字符集的长度 (也就是我们的"进制基数")
        int charsetLength = charset.length();

        // 2. 动态计算当前配置的物理最大容量 ( Base 的 Length 次方 )
        long maxCapacity = (long) Math.pow(charsetLength, numbersLength);

        // 3. 绝对安全防御
        if (count > maxCapacity) {
            throw new BusinessException(String.format(
                    "配置错误：使用字符集[%s]，长度为%d，最多只能生成%d个号码。您要求生成%d个，会导致死循环！",
                    charset, numbersLength, maxCapacity, count
            ));
        }

        // 4. 智能路由
        // 如果需求量超过容量的 40%，或者总容量小于 100 万，走洗牌法
        boolean useShuffleStrategy = (maxCapacity <= 1000000) || (count > maxCapacity * 0.4);

        if (useShuffleStrategy) {
            log.info("触发【动态洗牌战术】: 字符集 {}, 容量 {}, 需求 {}", charset, maxCapacity, count);
            return generateByShuffle(charset, numbersLength, count, (int) maxCapacity);
        } else {
            log.info("触发【哈希去重战术】: 字符集 {}, 容量 {}, 需求 {}", charset, maxCapacity, count);
            return generateByHashSet(charset, numbersLength, count);
        }
    }

    /**
     * 战术一：动态进制洗牌法
     */
    private static List<String> generateByShuffle(String charset, int numbersLength, int count, int maxCapacity) {
        List<String> rawNumbers = new ArrayList<>(maxCapacity);
        int charsetLength = charset.length();

        // 顺序生成所有可能组合
        for (int i = 0; i < maxCapacity; i++) {
            // 将十进制的 i，转换为指定的 base 进制，并映射到字符集上
            rawNumbers.add(convertToBaseString(i, charsetLength, numbersLength, charset));
        }

        // 彻底打乱并截取
        Collections.shuffle(rawNumbers);
        return rawNumbers.subList(0, count);
    }

    /**
     * 将十进制数字转换为 N进制 字符串 (核心映射算法)
     */
    private static String convertToBaseString(int index, int charsetLength, int numbersLength, String charset) {
        StringBuilder sb = new StringBuilder(numbersLength);
        int temp = index;

        // 经典进制转换：不断取余数
        for (int i = 0; i < numbersLength; i++) {
            int rem = temp % charsetLength;
            // 通过余数作为下标，去字符集里捞出对应的真实字符
            sb.append(charset.charAt(rem));
            temp /= charsetLength;
        }

        // 因为是从低位向高位取余，最后需要反转一下
        return sb.reverse().toString();
    }

    /**
     * 战术二：哈希去重法 (代码几乎不变，只需传入动态 charset)
     */
    private static List<String> generateByHashSet(String charset, int length, int count) {
        Set<String> uniqueNumbers = new HashSet<>((int) (count / 0.75) + 1);

        while (uniqueNumbers.size() < count) {
            uniqueNumbers.add(SolvelaRandomUtil.randomString(charset, length));
        }
        return new ArrayList<>(uniqueNumbers);
    }
}