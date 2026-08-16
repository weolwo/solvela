package sa.base.module.support.datamasking;

import sa.base.common.util.SmartStringUtil;

/**
 * 脱敏计算工具类 (重构后的工业级版本)
 * 职责：只做字符串截取，不做反射，不改对象
 *
 * 各类型的保留位数原本由 hutool DesensitizedUtil 决定，移除 hutool 时逐条抄了回来，
 * 规则见 DataMaskingUtilTest —— 那里的期望值是与 hutool 逐条比对过的，改规则前先看它。
 */
public class DataMaskingUtil {

    public static String apply(Object value, DataMaskingTypeEnum type) {
        if (value == null) {
            return null;
        }
        String origin = String.valueOf(value);
        if (origin.isEmpty()) {
            return origin;
        }

        // 根据类型直接匹配，跳过所有复杂的反射逻辑
        switch (type) {
            case PHONE:
                // 保留前 3 后 4：138****8000
                return SmartStringUtil.hide(origin, 3, origin.length() - 4);
            case CHINESE_NAME:
                // 只留姓：张**
                return SmartStringUtil.hide(origin, 1, origin.length());
            case ID_CARD:
                // 保留前 6 后 2
                return maskIdCard(origin, 6, 2);
            case EMAIL:
                return maskEmail(origin);
            case PASSWORD:
                return "******";
            case BANK_CARD:
                return maskBankCard(origin);
            default:
                // 自定义规则：保留前1/3和后1/3，中间打码
                return defaultMask(origin);
        }
    }

    /**
     * 身份证：保留前 front 位与后 end 位。
     * 位数不够时返回空串而不是原文 —— 宁可什么都不显示，也不能把完整号码漏出去。
     */
    private static String maskIdCard(String idCard, int front, int end) {
        if (front + end > idCard.length() || front < 0 || end < 0) {
            return "";
        }
        return SmartStringUtil.hide(idCard, front, idCard.length() - end);
    }

    /**
     * 邮箱：保留首字母与 @ 之后的全部，中间打码。
     * @ 在第 0/1 位（如 a@b.com）时原样返回 —— 只有一个字符可留，打了码也等于没打。
     */
    private static String maskEmail(String email) {
        int index = email.indexOf('@');
        if (index <= 1) {
            return email;
        }
        return SmartStringUtil.hide(email, 1, index);
    }

    /**
     * 银行卡：保留前 4 位与末尾一组，中间按 4 位一组打空格分隔，形如 6217 **** **** 1234。
     * 不足 9 位的原样返回（那多半不是卡号）。
     */
    private static String maskBankCard(String bankCardNo) {
        // 是 cleanBlank 不是 trim：卡号常带分组空格，只去首尾会让长度多算、分组全错
        String cardNo = SmartStringUtil.cleanBlank(bankCardNo);
        if (SmartStringUtil.isBlank(cardNo) || cardNo.length() < 9) {
            return cardNo;
        }
        int length = cardNo.length();
        int endLength = length % 4 == 0 ? 4 : length % 4;
        int midLength = length - 4 - endLength;
        StringBuilder buf = new StringBuilder();
        buf.append(cardNo, 0, 4);
        for (int i = 0; i < midLength; i++) {
            if (i % 4 == 0) {
                buf.append(' ');
            }
            buf.append('*');
        }
        buf.append(' ').append(cardNo, length - endLength, length);
        return buf.toString();
    }

    private static String defaultMask(String value) {
        int len = value.length();
        if (len <= 1) {
            return "*";
        }
        if (len <= 4) {
            return SmartStringUtil.hide(value, 1, len);
        }
        // 动态计算遮罩区间，性能远高于原版的 if-else 嵌套
        return SmartStringUtil.hide(value, len / 4, len - len / 4);
    }
}
