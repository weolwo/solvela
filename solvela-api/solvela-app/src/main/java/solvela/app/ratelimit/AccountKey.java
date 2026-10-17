package solvela.app.ratelimit;

import lombok.extern.slf4j.Slf4j;
import solvela.member.util.MemberEmailUtil;
import solvela.member.util.MemberPhoneUtil;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.Locale;

/**
 * 匿名场景（登录、注册、发码、重置密码）账号维度的计数值：请求体里填的邮箱 / 手机号，规范化后取 HMAC。
 *
 * <ul>
 *   <li><b>先规范化</b>：按会员域的同一套规则 —— 大小写不同的邮箱必须数成同一个账号；</li>
 *   <li><b>再取 HMAC</b>：计数键会进 Redis、进后台的处罚查询页，不能是明文邮箱手机号。</li>
 * </ul>
 * 没配密钥（{@code solvela.app.rate-limit.account-key}）时整个账号维度跳过，而不是退回明文。
 *
 * @Date 2026-10-17
 */
@Slf4j
final class AccountKey {

    private final SecretKeySpec key;

    AccountKey(String secret) {
        this.key = secret == null ? null : new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    }

    /** 计数值 {@code a:<HMAC 前 16 字节>}；没有可数的东西返回 null（跳过账号维度） */
    String of(String rawAccount) {
        if (key == null || rawAccount == null || rawAccount.isBlank()) {
            return null;
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(key);
            byte[] digest = mac.doFinal(normalize(rawAccount).getBytes(StandardCharsets.UTF_8));
            return "a:" + HexFormat.of().formatHex(digest, 0, 16);
        } catch (Exception e) {
            log.error("【网关限流】账号键计算失败，跳过账号维度", e);
            return null;
        }
    }

    /** 格式不对的输入会被会员域拒绝；这里照样数（按小写原文），免得「故意填错格式」成了绕开账号维度的办法 */
    private static String normalize(String raw) {
        String normalized = raw.contains("@") ? MemberEmailUtil.normalize(raw) : MemberPhoneUtil.normalize(raw);
        return normalized != null ? normalized : raw.strip().toLowerCase(Locale.ROOT);
    }
}
