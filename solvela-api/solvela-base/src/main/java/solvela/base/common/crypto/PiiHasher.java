package solvela.base.common.crypto;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import solvela.base.common.exception.BusinessException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.HexFormat;

/**
 * 个人信息（PII）的<b>可检索摘要</b>：HMAC-SHA256(密钥, 明文) 的 32 字节输出，以小写 hex 返回。
 *
 * <h3>为什么密文之外还要一列 hash</h3>
 * {@link PiiCipher} 用的 AES-GCM 每次加密都换随机 IV，<b>同一个手机号两次加密得到两串不同的密文</b>——
 * 这正是它安全的原因，但也意味着密文列上：
 * <ul>
 *   <li>建不了唯一索引（同号可以重复注册，且两行谁都发现不了）；</li>
 *   <li>没法按手机号登录（拿明文加密一遍去 where 匹配，永远匹配不上）。</li>
 * </ul>
 * 所以 {@code t_member} 是「密文 + hash 双写」：密文给「读出来展示/发短信」，
 * hash 给「唯一约束 + 登录查询」。缺一不可，见 {@code 数据库SQL脚本/member.sql} 的列注释。
 *
 * <h3>为什么是 HMAC 而不是 SHA-256(手机号 + 盐)</h3>
 * 手机号的全集只有 11 位、前 3 位还是固定号段 —— 大约 10^8 量级。
 * 纯哈希（哪怕加一个存在库里的盐）可以被<b>离线穷举</b>：脱库的人把号段全跑一遍就还原了所有手机号，
 * 加密那一列等于白做。HMAC 的密钥<b>不在库里</b>，只有拿到密钥才能算出摘要，穷举无从下手。
 *
 * <p>🔴 因此密钥必须和数据库分开保管（配置中心 / KMS / 环境变量），
 * <b>不要和库备份放在同一个地方</b>——放一起就退化成了纯哈希。
 *
 * <h3>🔴 密钥一旦用于生产就不能再改</h3>
 * 改了之后，老数据的 hash 全部对不上：会员再也登录不进来（按新 hash 查不到行），
 * 而唯一索引又拦不住他用同一个号重新注册 —— 于是一个手机号变成两个账号。
 * 与 {@link PiiCipher} 的密钥不同，这里连「用旧密钥读、新密钥写」的迁移都做不了，
 * 因为 hash 不可逆，重算的前提是能拿到手机号明文（那要先用 PiiCipher 把整表解一遍）。
 *
 * <h3>返回 hex 而不是 byte[]</h3>
 * 列类型是 {@code binary(32)}（hex 编码会把索引白白撑大一倍）。但 MyBatis 把 binary 列
 * 映射成 String 会经历一次字符集编解码，非 UTF-8 字节序列在这一步被替换成 {@code ?}，
 * 表现是「写进去和读出来不是同一个值」，而且只在部分摘要上发生 —— 极难查。
 * 所以约定：<b>Java 侧一律用 hex 字符串，SQL 侧用 {@code UNHEX(#{hex})} / {@code HEX(列)} 转换</b>，
 * 二进制永远不进入 Java 类型系统。
 *
 * @Date 2026-08-25
 */
@Component
public class PiiHasher {

    private static final String ALGORITHM = "HmacSHA256";

    private final SecretKeySpec key;

    public PiiHasher(@Value("${solvela.crypto.pii.hmac-key:}") String hmacKey) {
        if (hmacKey == null || hmacKey.isBlank()) {
            // 理由同 PiiCipher：不给默认密钥。给了的话，忘配置的人会用一个全世界都知道的密钥
            // 生成摘要，功能完全正常，而「可检索摘要」的防护值直接归零，且没有任何迹象。
            throw new IllegalStateException(
                    "solvela.crypto.pii.hmac-key 未配置：手机号/邮箱的唯一约束与登录查询依赖它，"
                  + "不允许用默认密钥兜底。请在配置中心/环境变量里注入。");
        }
        this.key = new SecretKeySpec(hmacKey.getBytes(StandardCharsets.UTF_8), ALGORITHM);
    }

    /**
     * 计算摘要，返回 64 位小写 hex。
     *
     * <p>🔴 入参必须是<b>已规范化</b>的明文。同一个手机号写成 {@code "138 0000 0000"}、
     * {@code "+8613800000000"}、{@code "13800000000"} 会得到三个不同的摘要 ——
     * 唯一索引拦不住，同一个人就能注册出三个账号。规范化由调用方负责（见
     * {@code MemberPhoneUtil#normalize}），这里刻意不做「顺手 trim 一下」之类的
     * 半吊子处理：做一半反而让人以为规范化已经有人管了。
     *
     * @param normalizedPlain 规范化后的明文，null / 空串返回 null（对应列上的 NULL，
     *                        注销释放手机号靠的就是把这一列置 NULL）
     */
    public String hash(String normalizedPlain) {
        if (normalizedPlain == null || normalizedPlain.isEmpty()) {
            return null;
        }
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(key);
            return HexFormat.of().formatHex(mac.doFinal(normalizedPlain.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            // HmacSHA256 是 JDK 必备算法、密钥也在构造器校验过，走到这里只可能是运行环境被改坏了。
            // 绝不能吞掉返回 null：那会让登录查询变成「按 NULL 查」，行为是「查不到人」而不是报错。
            throw new BusinessException("PII 摘要计算失败", e);
        }
    }
}
