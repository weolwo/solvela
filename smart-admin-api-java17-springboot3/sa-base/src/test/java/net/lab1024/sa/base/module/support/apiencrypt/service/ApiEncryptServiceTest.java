package net.lab1024.sa.base.module.support.apiencrypt.service;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 接口加解密的黄金向量测试。
 *
 * 🔴 <b>这是登录链路</b>：前端 src/lib/encrypt.js 用 sm-crypto 做同一套 SM4，
 * 登录密码就走这条路。这里任何一条断言挂掉，都意味着**全员登录不了**。
 *
 * 实现从 hutool 换成 BouncyCastle 时做过字节级比对：14 个明文样本，
 * 新旧两侧互相能解开对方的密文，密文串完全相同，0 处不一致。
 * 下面的黄金向量 {@code GOLDEN_123456} 是**前端实测能登录成功的那一串**，
 * 不是从当前实现反推出来的 —— 它同时钉住了后端实现和前后端契约。
 *
 * @Date 2026-08-08
 */
public class ApiEncryptServiceTest {

    /**
     * 明文 123456 经前端 sm-crypto 加密后的结果，已实测能登录成功。
     * 🔴 这个常量不能「按当前实现重新生成一遍」—— 那样测试就只是在自证，
     * 前后端一旦不一致也发现不了。
     */
    private static final String GOLDEN_123456 = "NzFlNWI2MjE5ODUyODAxOWM0OTI3ZDhkM2ZkNWY1ODY=";

    private final ApiEncryptServiceSmImpl sm4 = new ApiEncryptServiceSmImpl();

    private final ApiEncryptServiceAesImpl aes = new ApiEncryptServiceAesImpl();

    @Test
    public void sm4_黄金向量必须双向对齐() {
        assertEquals("123456", sm4.decrypt(GOLDEN_123456), "解不出 123456：前端传来的密码将全部解析失败");
        assertEquals(GOLDEN_123456, sm4.encrypt("123456"), "加密结果与前端不一致：双向契约已破");
    }

    @Test
    public void sm4_密文是hex外再套base64的双层结构() {
        // 重写时最容易漏掉中间的 hex 这一层。这里把结构本身钉住：
        // base64 解开之后，必须是一串纯十六进制字符，而不是原始密文字节
        String cipher = sm4.encrypt("123456");
        String inner = new String(Base64.getDecoder().decode(cipher), StandardCharsets.UTF_8);
        assertEquals("71e5b62198528019c4927d8d3fd5f586", inner);
        assertTrue(inner.matches("[0-9a-f]+"), () -> "内层应为十六进制小写串，实际为：" + inner);
        // 一个分组 16 字节 → 32 个 hex 字符
        assertEquals(32, inner.length());
    }

    @Test
    public void sm4_各类明文往返一致() {
        for (String plain : new String[]{
                "", "a", "123456", "P@ssw0rd!", "中文密码测试", "  前后有空格  ",
                "0123456789abcdef", "0123456789abcdefg",
                "!@#$%^&*()_+-=[]{}|;':\",./<>?", "a".repeat(1000)}) {
            assertEquals(plain, sm4.decrypt(sm4.encrypt(plain)), () -> "往返失败：" + plain);
        }
    }

    @Test
    public void sm4_ECB模式下相同明文产生相同密文() {
        // 不是在褒扬 ECB，而是把「前后端约定的既有行为」钉住：
        // 一旦有人把模式改成 CBC/GCM，这里会立刻失败，提醒他前端 sm-crypto 那侧也得同步改
        assertEquals(sm4.encrypt("123456"), sm4.encrypt("123456"));
    }

    @Test
    public void sm4_解密失败时返回空串而不是抛异常() {
        // 请求体是外部输入，随便传一串垃圾不能把接口打挂
        assertEquals("", sm4.decrypt("这不是合法的base64!!!"));
        assertEquals("", sm4.decrypt("YWJj"));
    }

    @Test
    public void aes_只有一层base64_没有中间的hex() {
        // 与 SM4 的双层结构不同，别把两边的做法搞混
        String cipher = aes.encrypt("123456");
        assertEquals(16, Base64.getDecoder().decode(cipher).length, "应为一个 AES 分组的原始字节");
        assertEquals("123456", aes.decrypt(cipher));
    }

    @Test
    public void aes_各类明文往返一致() {
        for (String plain : new String[]{"", "a", "123456", "中文密码测试", "a".repeat(1000)}) {
            assertEquals(plain, aes.decrypt(aes.encrypt(plain)), () -> "往返失败：" + plain);
        }
    }

    @Test
    public void aes与sm4的密文不可互换() {
        // 两套算法用了同一个密钥字符串，很容易误以为可以混用
        assertNotEquals(aes.encrypt("123456"), sm4.encrypt("123456"));
        assertEquals("", sm4.decrypt(aes.encrypt("123456")));
    }
}
