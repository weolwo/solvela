package net.lab1024.sa.base.module.support.datamasking;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 脱敏规则固化测试。
 *
 * 规则原本来自 hutool DesensitizedUtil，移除 hutool 时逐条抄了回来，
 * 期望值是与 hutool 跑差异比对得出的（27 个样本 × 6 种类型，0 处不一致），不是照感觉写的。
 *
 * 🔴 脱敏是「漏了就无法挽回」的一类逻辑：多打一个星号只是难看，少打一个就是个人信息泄漏。
 * 改任何一条规则前，先让这些用例继续通过。
 *
 * @Date 2026-08-08
 */
public class DataMaskingUtilTest {

    @Test
    public void 手机号保留前3后4() {
        assertEquals("138****8000", DataMaskingUtil.apply("13800138000", DataMaskingTypeEnum.PHONE));
        // 长度不够以致「要打码的区间」为空时原样返回，不抛异常
        assertEquals("1380013", DataMaskingUtil.apply("1380013", DataMaskingTypeEnum.PHONE));
    }

    @Test
    public void 中文名只留姓() {
        assertEquals("张*", DataMaskingUtil.apply("张三", DataMaskingTypeEnum.CHINESE_NAME));
        assertEquals("欧***", DataMaskingUtil.apply("欧阳娜娜", DataMaskingTypeEnum.CHINESE_NAME));
        assertEquals("张", DataMaskingUtil.apply("张", DataMaskingTypeEnum.CHINESE_NAME));
    }

    @Test
    public void 身份证保留前6后2_位数不够时返回空串() {
        assertEquals("410102**********34", DataMaskingUtil.apply("410102199001011234", DataMaskingTypeEnum.ID_CARD));
        assertEquals("410102**********3X", DataMaskingUtil.apply("41010219900101123X", DataMaskingTypeEnum.ID_CARD));
        // 🔴 位数不够时返回空串而不是原文：宁可什么都不显示，也不能把完整号码漏出去
        assertEquals("", DataMaskingUtil.apply("1234567", DataMaskingTypeEnum.ID_CARD));
        assertEquals("", DataMaskingUtil.apply("abc", DataMaskingTypeEnum.ID_CARD));
    }

    @Test
    public void 邮箱保留首字母与域名() {
        assertEquals("a*****@gmail.com", DataMaskingUtil.apply("abcdef@gmail.com", DataMaskingTypeEnum.EMAIL));
        assertEquals("a*@b.com", DataMaskingUtil.apply("ab@b.com", DataMaskingTypeEnum.EMAIL));
        // @ 在第 0/1 位时原样返回：只剩一个字符可留，打码没有意义
        assertEquals("a@b.com", DataMaskingUtil.apply("a@b.com", DataMaskingTypeEnum.EMAIL));
        assertEquals("@x.com", DataMaskingUtil.apply("@x.com", DataMaskingTypeEnum.EMAIL));
        // ⚠️ 没有 @ 时**原样返回**，一个字符都不打码。这是从 hutool 继承下来的行为，
        // 意味着「邮箱字段里塞了非邮箱内容」时脱敏是失效的 —— 别指望 EMAIL 类型能兜住任意字符串
        assertEquals("noatsign", DataMaskingUtil.apply("noatsign", DataMaskingTypeEnum.EMAIL));
    }

    @Test
    public void 银行卡按4位分组_且先去掉所有空格() {
        assertEquals("6217 **** **** 1234", DataMaskingUtil.apply("6217000010001234", DataMaskingTypeEnum.BANK_CARD));
        // 🔴 是去掉所有空白而不是只 trim 首尾：卡号常带分组空格，
        // 只 trim 会把空格算进长度，分组和保留位数全错
        assertEquals("6217 **** **** 1234", DataMaskingUtil.apply("  6217 0000 1000 1234  ", DataMaskingTypeEnum.BANK_CARD));
        assertEquals("6217 **** **** 123", DataMaskingUtil.apply("621700001000123", DataMaskingTypeEnum.BANK_CARD));
        // 17 位：17%4=1，所以末尾只留 1 位
        assertEquals("6217 **** **** **** 5", DataMaskingUtil.apply("62170000100012345", DataMaskingTypeEnum.BANK_CARD));
        // 不足 9 位的原样返回（那多半不是卡号）
        assertEquals("12345678", DataMaskingUtil.apply("12345678", DataMaskingTypeEnum.BANK_CARD));
    }

    @Test
    public void 密码恒定六星_不泄漏长度() {
        assertEquals("******", DataMaskingUtil.apply("P@ssw0rd!", DataMaskingTypeEnum.PASSWORD));
        assertEquals("******", DataMaskingUtil.apply("a", DataMaskingTypeEnum.PASSWORD));
    }

    @Test
    public void 通用规则保留前后各四分之一() {
        assertEquals("*", DataMaskingUtil.apply("a", DataMaskingTypeEnum.COMMON));
        assertEquals("a*", DataMaskingUtil.apply("ab", DataMaskingTypeEnum.COMMON));
        assertEquals("a***", DataMaskingUtil.apply("abcd", DataMaskingTypeEnum.COMMON));
        assertEquals("a***e", DataMaskingUtil.apply("abcde", DataMaskingTypeEnum.COMMON));
        assertEquals("中****试", DataMaskingUtil.apply("中文名字测试", DataMaskingTypeEnum.COMMON));
        // 地址、车牌、座机、用户id 都走同一条默认规则
        assertEquals("中****试", DataMaskingUtil.apply("中文名字测试", DataMaskingTypeEnum.ADDRESS));
        assertEquals("中****试", DataMaskingUtil.apply("中文名字测试", DataMaskingTypeEnum.CAR_LICENSE));
    }

    @Test
    public void null与空串原样返回() {
        assertNull(DataMaskingUtil.apply(null, DataMaskingTypeEnum.PHONE));
        assertEquals("", DataMaskingUtil.apply("", DataMaskingTypeEnum.PHONE));
        assertEquals("", DataMaskingUtil.apply("", DataMaskingTypeEnum.ID_CARD));
    }

    @Test
    public void 非字符串入参走toString() {
        assertEquals("138******000", DataMaskingUtil.apply(138001380000L, DataMaskingTypeEnum.USER_ID));
    }

    @Test
    public void 代理对不会被劈开() {
        // 一个 emoji 是两个 char。hide 按码点遍历，不会把代理对拆散成乱码方块
        String masked = DataMaskingUtil.apply(FIVE_EMOJI, DataMaskingTypeEnum.COMMON);
        assertFalse(masked.contains("�"), () -> "出现替换字符，说明代理对被劈开了：" + masked);
        assertTrue(masked.startsWith(EMOJI), () -> "首个 emoji 被劈开了：" + masked);

        // ⚠️ 结果是「保留前 2 个、后面 3 个打码」而不是对称的前后各留：
        // defaultMask 用 String#length()（char 数 = 10）算区间，而 hide 按码点（5 个）打码，
        // 两把尺子不一致。这是从 hutool 时代就有的行为，此处只是把它钉住，不是在背书。
        assertEquals(EMOJI + EMOJI + "***", masked);
    }

    /** U+1F600，一个需要代理对表示的字符 */
    private static final String EMOJI = new String(Character.toChars(0x1F600));

    private static final String FIVE_EMOJI = EMOJI.repeat(5);
}
