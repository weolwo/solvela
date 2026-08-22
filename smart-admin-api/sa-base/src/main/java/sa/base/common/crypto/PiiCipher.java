package sa.base.common.crypto;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import sa.base.common.exception.BusinessException;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * 个人信息（PII）字段级加密：<b>存进数据库的收件人姓名 / 电话 / 详细地址走这里</b>。
 *
 * <p><b>为什么不复用已有的 {@code ApiEncryptServiceAesImpl}</b> —— 那个是<b>接口传输</b>加密，
 * 三条都不能用于落库：
 * <ol>
 *   <li>它是 <b>ECB</b> 模式。ECB 下相同明文恒产出相同密文 —— 同一个收货地址在表里长得一模一样，
 *       不用解密就能按密文分组、数出「哪些人住同一个地址」，甚至用已知明文做字典比对；</li>
 *   <li>密钥 {@code "1024lab__1024lab"} <b>硬编码在源码里</b>，和「密钥与数据库分开保管」正好相反；</li>
 *   <li>它出错时 {@code return ""} —— 用在落库上就是<b>把用户地址悄悄变成空串</b>，
 *       而且这一行再也救不回来。</li>
 * </ol>
 *
 * <h3>格式</h3>
 * <pre>
 *   P1:&lt;base64( iv(12B) || ciphertext+tag(16B) )&gt;
 * </pre>
 * <ul>
 *   <li><b>AES-256-GCM</b>：带认证标签，密文被改一个字节解密就会失败，而不是解出一段垃圾；</li>
 *   <li><b>每次加密都用新的随机 IV</b>，所以同一个地址两次加密的密文不同 —— 这正是 ECB 缺的那一条；</li>
 *   <li><b>{@code P1:} 前缀是格式版本</b>。将来换算法/换密钥时它是唯一能区分新旧数据的东西；
 *       没有它，密钥轮换那天你分不清哪些行已经重新加密过。</li>
 * </ul>
 *
 * <h3>🔴 空串与 null 一律原样返回，绝不加密</h3>
 * 不是为了省 CPU，是因为库里有查询依赖「空」这个状态：
 * {@code PhysicalDeliveryMapper.selectStatusStat} 用
 * {@code receiver_address IS NULL OR receiver_address = ''} 数「收件信息没补全、想发也发不了」的单子。
 * 一旦把空串也加密成一段密文，那个统计会<b>静默归零</b> —— 页面上「待补地址」永远是 0，
 * 而运营正是靠它去催用户的。
 *
 * <h3>密钥</h3>
 * 取自配置 {@code smart.crypto.pii.secret-key}，经 SHA-256 派生成 32 字节，所以配置串长度随意。
 * <p>🔴 <b>生产环境必须走环境变量/配置中心，不要跟着代码库走，更不要和数据库备份放在同一个地方</b> ——
 * 密钥和密文躺在一起，等于没加密。
 * <p>🔴 <b>密钥一旦用于加密就不能改</b>：改了之后旧数据全部解不开（GCM 会认证失败并抛异常，
 * 不会解出乱码——这是好事，至少它不会静默）。真要轮换得先把存量行用旧密钥读出、新密钥写回，
 * 那时 {@code P1:} 前缀就是分辨进度的依据。
 *
 * <h3>它防的是什么，不防什么</h3>
 * 防的是<b>静态泄露</b>：库被脱、备份被拷、DBA 直接 select，看到的是密文。
 * <b>不防</b>有应用权限的人 —— 后台运营打开发货单看到的仍是明文（不然没法发货）。
 * 「谁能看到完整收件信息」是权限设计问题，不是加密能解决的。
 *
 * @Date 2026-08-22
 */
@Slf4j
@Component
public class PiiCipher {

    /** 格式版本前缀。改算法时递增，<b>不要复用</b> */
    public static final String PREFIX = "P1:";

    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final String ALGORITHM = "AES";

    /** GCM 推荐 12 字节 IV：这是 GCM 唯一不需要额外做 GHASH 的长度，也是各语言实现的默认值 */
    private static final int IV_LENGTH = 12;

    /** 认证标签 128 位 */
    private static final int TAG_BITS = 128;

    /**
     * IV 必须<b>不可预测且不重复</b>。GCM 的致命误用是「同一密钥下 IV 重复」——
     * 那会直接泄露明文异或值并让伪造成为可能。SecureRandom 是线程安全的，共用一个实例即可。
     */
    private static final SecureRandom RANDOM = new SecureRandom();

    private final SecretKeySpec key;

    public PiiCipher(@Value("${smart.crypto.pii.secret-key:}") String secretKey) {
        if (secretKey == null || secretKey.isBlank()) {
            // 🔴 不给默认密钥。给了的话，谁忘了配置就会用一个全世界都知道的密钥把用户地址加密进库，
            //    而且一切看起来都正常 —— 这种「配置漏了但功能照跑」正是最难发现的一类问题。
            throw new IllegalStateException(
                    "smart.crypto.pii.secret-key 未配置：收件人信息是个人信息，必须加密落库，"
                  + "不允许用默认密钥兜底。请在配置中心/环境变量里注入。");
        }
        this.key = new SecretKeySpec(sha256(secretKey), ALGORITHM);
    }

    /**
     * 加密。null 与空串原样返回（见类注释里那条红线）。
     */
    public String encrypt(String plain) {
        if (plain == null || plain.isEmpty()) {
            return plain;
        }
        if (plain.startsWith(PREFIX)) {
            // 已经是密文，重复加密会套娃，读的时候只解开一层就得到一段 "P1:..." 字符串
            return plain;
        }
        try {
            byte[] iv = new byte[IV_LENGTH];
            RANDOM.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] ct = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));

            byte[] out = new byte[iv.length + ct.length];
            System.arraycopy(iv, 0, out, 0, iv.length);
            System.arraycopy(ct, 0, out, iv.length, ct.length);
            return PREFIX + Base64.getEncoder().encodeToString(out);
        } catch (Exception e) {
            // 🔴 绝不 return "" —— 加密失败时把空串写进库，等于悄悄抹掉用户的收件信息
            throw new BusinessException("收件信息加密失败", e);
        }
    }

    /**
     * 解密。null / 空串 / <b>没有 {@link #PREFIX} 前缀的值</b> 原样返回。
     *
     * <p>为什么无前缀时不抛异常：库里理论上不该有明文，但「有人手工改库塞了一行明文」是
     * 真实会发生的事。为此让整个列表页 500，比让运营看到那一行明文更糟 —— 前者谁都干不了活。
     * 所以这里放行并打 WARN，让它以「日志里有一条刺眼告警」的形式被发现。
     */
    public String decrypt(String stored) {
        if (stored == null || stored.isEmpty()) {
            return stored;
        }
        if (!stored.startsWith(PREFIX)) {
            log.warn("[PII] 读到未加密的明文值，长度={}。库里不该有明文，"
                   + "多半是有人手工 INSERT/UPDATE 绕过了应用层，请核查", stored.length());
            return stored;
        }
        try {
            byte[] raw = Base64.getDecoder().decode(stored.substring(PREFIX.length()));
            byte[] iv = new byte[IV_LENGTH];
            System.arraycopy(raw, 0, iv, 0, IV_LENGTH);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] plain = cipher.doFinal(raw, IV_LENGTH, raw.length - IV_LENGTH);
            return new String(plain, StandardCharsets.UTF_8);
        } catch (Exception e) {
            // 到这里说明「有前缀、但解不开」：密钥换过、密文被截断、或者数据被改过。
            // 这三种都必须炸出来 —— 静默返回 null 会让页面上少一列，没人会发现数据已经废了。
            throw new BusinessException("收件信息解密失败：密钥不匹配或密文已损坏", e);
        }
    }

    /**
     * 给定明文字节数，算出密文字符数。
     *
     * <p>用途是<b>定列宽</b>：密文塞不下时 MySQL 非严格模式会静默截断，
     * 表现为「存进去了，读出来解密失败」，而且那一行救不回来。
     *
     * <p>公式：{@code 前缀3 + base64(12 + n + 16)}，其中 12 是 IV、16 是 GCM 标签。
     * 实际取值（UTF-8 中文按 3 字节算）：
     * <pre>
     *   收件人姓名  40 字符 -> 120 字节 -> 203 字符  ≤ varchar(255) ✅
     *   收件人电话  30 字符 ->  30 字节 ->  83 字符  ≤ varchar(255) ✅
     *   详细地址   100 字符 -> 300 字节 -> 443 字符  ≤ varchar(512) ✅
     * </pre>
     * 表单上的 {@code @Size} 就是按这三行定的，改列宽或改 @Size 时把这里一起重算。
     */
    public static int cipherTextLength(int plainBytes) {
        int payload = IV_LENGTH + plainBytes + TAG_BITS / 8;
        return PREFIX.length() + ((payload + 2) / 3) * 4;
    }

    private static byte[] sha256(String s) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 不可用", e);
        }
    }
}
