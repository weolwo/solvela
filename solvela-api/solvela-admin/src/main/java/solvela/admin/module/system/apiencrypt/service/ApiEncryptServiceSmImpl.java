package solvela.admin.module.system.apiencrypt.service;

import lombok.extern.slf4j.Slf4j;
import solvela.base.common.constant.StringConst;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.security.Security;
import java.util.Base64;
import java.util.HexFormat;

/**
 * 国产 SM4 加密 和 解密
 * 1、国密SM4 要求秘钥为 128bit，转化字节为 16个字节；
 * 2、js前端使用 UCS-2 或者 UTF-16 编码，字母、数字、特殊符号等 占用1个字节；
 * 3、java中 每个 字母数字 也是占用1个字节；
 * 4、所以：前端和后端的 秘钥Key 组成为：字母、数字、特殊符号 一共16个即可
 *
 * <p>
 * 🔴 <b>这条链路上是登录密码</b>：前端 src/lib/encrypt.js 用 sm-crypto 做同一套 SM4，
 * 改错了就是全员登录不了。移除 hutool 时把实现换成了 BouncyCastle（bcprov 本就是现成依赖），
 * 参数必须逐项对齐，任何一项不同都会让密文对不上：
 * <ul>
 *     <li><b>ECB 模式、PKCS5(=PKCS7) 填充</b>、无 IV —— 与原 hutool {@code new SM4(key)} 的默认值一致</li>
 *     <li><b>密文是「双层编码」</b>：先 SM4 加密得到<b>十六进制小写串</b>，再把这个 hex 串
 *         当作文本做 Base64。重写时极易漏掉其中一层，漏了就对不上</li>
 *     <li>密钥字节 = {@link #SM4_KEY} 的 ASCII 字节（见 {@link #stringToHex}/{@link #hexToBytes}）</li>
 * </ul>
 * 黄金测试向量见 ApiEncryptServiceSmImplTest：明文 {@code 123456} 的密文恒为
 * {@code NzFlNWI2MjE5ODUyODAxOWM0OTI3ZDhkM2ZkNWY1ODY=}（前端实测能登录成功的那一串）。
 *
 * <p>
 * ⚠️ ECB 模式本身是有弱点的（相同明文块产生相同密文块，且无完整性保护），
 * 但它是**前后端约定好的既有契约**，前端 sm-crypto 那侧也是 ECB。要改成 CBC/GCM
 * 必须前后端同时改并处理存量，不属于「移除依赖」这轮的范围。
 *
 * @Author 1024创新实验室-主任:卓大
 * @Date 2023/10/21 11:41:46
 * @Wechat zhuoda1024
 * @Email lab1024@163.com
 * @Copyright <a href="https://1024lab.net">1024创新实验室</a>
 */

@Slf4j
@Service
public class ApiEncryptServiceSmImpl implements ApiEncryptService {

    private static final String SM4_KEY = "1024lab__1024lab";

    /**
     * ECB + PKCS5Padding，与原 hutool new SM4(key) 的默认值一致，不要改
     */
    private static final String TRANSFORMATION = "SM4/ECB/PKCS5Padding";

    private static final String ALGORITHM = "SM4";

    static {
        Security.addProvider(new BouncyCastleProvider());
    }

    @Override
    public String encrypt(String data) {
        try {
            // 第一步： SM4 加密，取十六进制小写串
            byte[] encrypted = cipher(Cipher.ENCRYPT_MODE).doFinal(data.getBytes(StandardCharsets.UTF_8));
            String encryptHex = HexFormat.of().formatHex(encrypted);

            // 第二步： 对 hex 串再做 Base64。这一层不能省 —— 前端解的就是这个双层结构
            return Base64.getEncoder().encodeToString(encryptHex.getBytes(StandardCharsets.UTF_8));

        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return StringConst.EMPTY;
        }
    }

    @Override
    public String decrypt(String data) {
        try {
            // 第一步： Base64 解码，还原出十六进制串
            String encryptHex = new String(Base64.getDecoder().decode(data), StandardCharsets.UTF_8);

            // 第二步： hex 转字节后 SM4 解密
            byte[] decrypted = cipher(Cipher.DECRYPT_MODE).doFinal(HexFormat.of().parseHex(encryptHex));
            return new String(decrypted, StandardCharsets.UTF_8);

        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return StringConst.EMPTY;
        }
    }

    /**
     * Cipher 不是线程安全的，每次调用都要新建，不能提成字段复用
     */
    private static Cipher cipher(int mode) throws Exception {
        Key key = new SecretKeySpec(hexToBytes(stringToHex(SM4_KEY)), ALGORITHM);
        Cipher cipher = Cipher.getInstance(TRANSFORMATION, BouncyCastleProvider.PROVIDER_NAME);
        cipher.init(mode, key);
        return cipher;
    }

    public static String stringToHex(String input) {
        char[] chars = input.toCharArray();
        StringBuilder hex = new StringBuilder();
        for (char c : chars) {
            hex.append(Integer.toHexString((int) c));
        }
        return hex.toString();
    }

    /**
     * 16 进制串转字节数组
     *
     * @param hex 16进制字符串
     * @return byte数组
     */
    public static byte[] hexToBytes(String hex) {
        int length = hex.length();
        byte[] result;
        if (length % 2 == 1) {
            length++;
            result = new byte[(length / 2)];
            hex = "0" + hex;
        } else {
            result = new byte[(length / 2)];
        }
        int j = 0;
        for (int i = 0; i < length; i += 2) {
            result[j] = hexToByte(hex.substring(i, i + 2));
            j++;
        }
        return result;
    }

    /**
     * 16 进制字符转字节
     *
     * @param hex 16进制字符 0x00到0xFF
     * @return byte
     */
    private static byte hexToByte(String hex) {
        return (byte) Integer.parseInt(hex, 16);
    }

}
