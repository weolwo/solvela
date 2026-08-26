package solvela.admin.module.system.apiencrypt.service;

import lombok.extern.slf4j.Slf4j;
import solvela.base.constant.StringConst;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Base64;

/**
 * AES 加密和解密
 * 1、AES加密算法支持三种密钥长度：128位、192位和256位，这里选择128位
 * 2、AES 要求秘钥为 128bit，转化字节为 16个字节；
 * 3、js前端使用 UCS-2 或者 UTF-16 编码，字母、数字、特殊符号等 占用1个字节；
 * 4、所以：秘钥Key 组成为：字母、数字、特殊符号 一共16个即可
 *
 * <p>
 * 移除 hutool 时把实现换成了 JDK 自带的 {@link Cipher}（AES 不需要 BouncyCastle）。
 * 模式与填充沿用原 hutool {@code new AES(key)} 的默认值：<b>ECB + PKCS5Padding，无 IV</b>。
 * 与 SM4 那边不同，<b>AES 这条链路只有一层 Base64</b>，没有中间的 hex 层。
 *
 * <p>
 * ⚠️ 当前没有任何地方注入这个实现（它没有 {@code @Service}，实际生效的是
 * {@link ApiEncryptServiceSmImpl}）。保留它是为了随时可切换，
 * 但真要启用前请先跑 ApiEncryptServiceAesImplTest 确认前端那侧的实现能对上。
 *
 * @Author 1024创新实验室-主任:卓大
 * @Date 2023/10/21 11:41:46
 * @Wechat zhuoda1024
 * @Email lab1024@163.com
 * @Copyright <a href="https://1024lab.net">1024创新实验室</a>
 */

@Slf4j
public class ApiEncryptServiceAesImpl implements ApiEncryptService {

    private static final String AES_KEY = "1024lab__1024lab";

    /**
     * ECB + PKCS5Padding，与原 hutool new AES(key) 的默认值一致，不要改
     */
    private static final String TRANSFORMATION = "AES/ECB/PKCS5Padding";

    private static final String ALGORITHM = "AES";

    @Override
    public String encrypt(String data) {
        try {
            //  AES 加密 并转为 base64
            byte[] encrypted = cipher(Cipher.ENCRYPT_MODE).doFinal(data.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(encrypted);

        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return StringConst.EMPTY;
        }
    }

    @Override
    public String decrypt(String data) {
        try {
            // 第一步： Base64 解码
            byte[] base64Decode = Base64.getDecoder().decode(data);

            // 第二步： AES 解密
            byte[] decryptedBytes = cipher(Cipher.DECRYPT_MODE).doFinal(base64Decode);
            return new String(decryptedBytes, StandardCharsets.UTF_8);

        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return StringConst.EMPTY;
        }
    }

    /**
     * Cipher 不是线程安全的，每次调用都要新建，不能提成字段复用
     */
    private static Cipher cipher(int mode) throws Exception {
        Key key = new SecretKeySpec(hexToBytes(stringToHex(AES_KEY)), ALGORITHM);
        Cipher cipher = Cipher.getInstance(TRANSFORMATION);
        cipher.init(mode, key);
        return cipher;
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

    public static String stringToHex(String input) {
        char[] chars = input.toCharArray();
        StringBuilder hex = new StringBuilder();
        for (char c : chars) {
            hex.append(Integer.toHexString((int) c));
        }
        return hex.toString();
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
