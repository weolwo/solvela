package solvela.crypto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import solvela.exception.BusinessException;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link PiiCipher} 的语义固化测试。
 *
 * <p>这里钉住的每一条都对应一种「错了不报错」的失败方式，
 * 不是为了覆盖率：加密出问题的典型表现不是抛异常，而是<b>数据悄悄坏掉</b>。
 */
class PiiCipherTest {

    private final PiiCipher cipher = new PiiCipher("unit-test-key-do-not-use-in-any-real-env");

    @Test
    @DisplayName("往返：加密再解密拿回原文，中文/emoji/超长都不能丢字节")
    void roundTrip() {
        for (String plain : new String[]{
                "张三",
                "13800138000",
                "浙江省杭州市西湖区文三路 100 号 xx 大厦 A 座 1801 室",
                "a", "0", " 前后有空格 ",
                "emoji🎁也要能过", "混合 mixed 123 -_/#@"}) {
            assertEquals(plain, cipher.decrypt(cipher.encrypt(plain)), "往返丢字：" + plain);
        }
    }

    @Test
    @DisplayName("🔴 null 与空串原样返回，绝不加密")
    void nullAndEmptyPassThrough() {
        // 不是省 CPU：PhysicalDeliveryMapper.selectStatusStat 用
        // `receiver_address IS NULL OR receiver_address = ''` 数「收件信息没补全」的单子。
        // 一旦把空串也加密成密文，那个统计会静默归零，而运营正是靠它去催用户的。
        assertNull(cipher.encrypt(null));
        assertNull(cipher.decrypt(null));
        assertEquals("", cipher.encrypt(""));
        assertEquals("", cipher.decrypt(""));
    }

    @Test
    @DisplayName("🔴 同一明文两次加密必须得到不同密文（ECB 就是栽在这一条上）")
    void sameInputDifferentCipherText() {
        String plain = "浙江省杭州市西湖区文三路 100 号";
        String a = cipher.encrypt(plain);
        String b = cipher.encrypt(plain);
        assertNotEquals(a, b,
                "两次密文相同 = 退化成 ECB：不用解密就能按密文分组数出「哪些人住同一个地址」");
        assertEquals(plain, cipher.decrypt(a));
        assertEquals(plain, cipher.decrypt(b));
    }

    @Test
    @DisplayName("密文带 P1: 前缀，且不含原文任何片段")
    void formatAndOpacity() {
        String enc = cipher.encrypt("13800138000");
        assertTrue(enc.startsWith(PiiCipher.PREFIX), "缺前缀就没法分辨新旧格式，密钥轮换那天会抓瞎");
        assertTrue(enc.indexOf("13800138000") < 0);
    }

    @Test
    @DisplayName("重复加密不套娃：已是密文的值原样返回")
    void encryptTwiceIsIdempotent() {
        String once = cipher.encrypt("张三");
        assertEquals(once, cipher.encrypt(once),
                "套娃之后读的时候只解开一层，会得到一段 P1: 开头的字符串");
    }

    @Test
    @DisplayName("🔴 密文被改一个字节 -> 解密抛异常，不能解出垃圾")
    void tamperedCipherTextThrows() {
        String enc = cipher.encrypt("浙江省杭州市西湖区文三路 100 号");
        // 翻转 base64 段里的一个字符
        char[] chars = enc.toCharArray();
        int idx = enc.length() - 5;
        chars[idx] = chars[idx] == 'A' ? 'B' : 'A';
        assertThrows(BusinessException.class, () -> cipher.decrypt(new String(chars)),
                "GCM 的认证标签就是干这个的：改过的密文必须解不开，而不是解出一段乱码");
    }

    @Test
    @DisplayName("🔴 换密钥之后解不开旧密文，且是抛异常而不是静默乱码")
    void wrongKeyThrows() {
        String enc = cipher.encrypt("张三");
        PiiCipher other = new PiiCipher("another-key-entirely");
        assertThrows(BusinessException.class, () -> other.decrypt(enc));
    }

    @Test
    @DisplayName("无前缀的明文原样返回：有人手工改库塞了明文时，页面不该整页 500")
    void legacyPlainTextPassThrough() {
        assertEquals("手工塞进去的明文", cipher.decrypt("手工塞进去的明文"));
    }

    @Test
    @DisplayName("🔴 空密钥直接启动失败，不给默认密钥兜底")
    void blankKeyRefusesToStart() {
        // 给了默认密钥的话，谁忘了配就会用一个全世界都知道的密钥把用户地址加密进库，
        // 而且一切看起来都正常
        assertThrows(IllegalStateException.class, () -> new PiiCipher(""));
        assertThrows(IllegalStateException.class, () -> new PiiCipher(null));
        assertThrows(IllegalStateException.class, () -> new PiiCipher("   "));
    }

    @Test
    @DisplayName("🔴 密文长度公式与实测一致 —— 列宽就是按它定的")
    void cipherTextLengthMatchesReality() {
        // 算错的后果是 MySQL 非严格模式静默截断密文：存进去了，读出来解密失败，那一行救不回来
        for (String plain : new String[]{
                "张三",
                "13800138000",
                "浙江省杭州市西湖区文三路 100 号 xx 大厦 A 座 1801 室"}) {
            int bytes = plain.getBytes(StandardCharsets.UTF_8).length;
            assertEquals(PiiCipher.cipherTextLength(bytes), cipher.encrypt(plain).length(),
                    "公式与实际长度对不上：" + plain);
        }
    }

    @Test
    @DisplayName("🔴 三列的上限明文在各自列宽内装得下（姓名40/电话30/地址100）")
    void maxPlainTextFitsColumn() {
        // 与 PhysicalDeliveryAddForm 的 @Size、PhysicalDeliveryService 的三个常量是同一组数字
        assertTrue(PiiCipher.cipherTextLength(40 * 3) <= 255,
                "姓名 40 字的密文超过 varchar(255)");
        assertTrue(PiiCipher.cipherTextLength(30) <= 255,
                "电话 30 位的密文超过 varchar(255)");
        assertTrue(PiiCipher.cipherTextLength(100 * 3) <= 512,
                "地址 100 字的密文超过 varchar(512)");

        // 真拿满长度的中文串跑一遍，别只信公式
        String name = "张".repeat(40);
        String address = "路".repeat(100);
        assertTrue(cipher.encrypt(name).length() <= 255);
        assertTrue(cipher.encrypt(address).length() <= 512);
        assertDoesNotThrow(() -> cipher.decrypt(cipher.encrypt(address)));
    }
}
