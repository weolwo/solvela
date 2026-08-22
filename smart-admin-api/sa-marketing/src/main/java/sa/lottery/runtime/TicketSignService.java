package sa.lottery.runtime;

import lombok.extern.slf4j.Slf4j;
import sa.base.common.exception.BusinessException;
import sa.lottery.record.domain.entity.LotteryRecord;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.HexFormat;

/**
 * 号码防篡改签名。
 *
 * <p>{@code t_lottery_record.security_sign} 是 {@code varchar(32) NOT NULL} 且<b>无默认值</b> ——
 * 不赋值时 MySQL 严格模式会直接拒绝插入。这正是本项目已复发 5 次的
 * 「NOT NULL 无默认值」缺陷模式，本类的存在就是为了让它不可能被漏掉：
 * 落库路径上只有一处构造记录，而那里必须调用 {@link #sign}。
 *
 * <p>签名覆盖「谁、在哪一期、拿到哪个号、对应哪个游标」四要素。
 * 有了它，即便有人直接改库把号码换成中奖号，也能在核销时验出来 ——
 * 号码本身可由 FPE 反解验真，签名再把「这个号属于这个人」钉死。
 *
 * <p>复用 FPE 的 masterSecret：两者的失效条件本来就一致（密钥一换，
 * 历史号码既反解不了、签名也验不过），拆成两个密钥只会多一个要同步管理的东西。
 *
 * @Author alaric
 * @Date 2026-07-28
 */
@Slf4j
@Service
public class TicketSignService {

    private static final String HMAC_ALGORITHM = "HmacSHA256";

    /**
     * security_sign 列宽 varchar(32)，取十六进制前 32 位（= HMAC 前 16 字节）。
     * 128 位对防篡改足够，且严格贴合列宽 —— 「写库字段超长」是本项目另一个反复出现的坑
     */
    private static final int SIGN_LENGTH = 32;

    @Value("${lottery.fpe.master-secret:}")
    private String masterSecret;

    /**
     * 生成签名。五要素任一被改动，签名都对不上。
     *
     * <p>🔴 <b>末位刻意仍是 memberName（账号快照），没有跟着 v3.71.0 换成 member_id</b>：
     * 签名口径一改，<b>所有存量号码的验真会集体失败</b>，客服那边表现为「记录被篡改」。
     * 而 {@code t_lottery_record.member_name} 是写完就不再变的快照
     * （会员改名不会回头改它），拿它当签名要素本身是自洽的 —— 换成会员号并不会更安全。
     */
    public String sign(String lotteryCode, String issueNo, long sequenceNo, String ticketNumber, String memberName) {
        if (masterSecret == null || masterSecret.isBlank()) {
            throw new BusinessException("FPE 主密钥未配置，无法生成号码签名");
        }
        String payload = lotteryCode + "|" + issueNo + "|" + sequenceNo + "|" + ticketNumber + "|" + memberName;
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(masterSecret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
            String hex = HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
            return hex.substring(0, SIGN_LENGTH);
        } catch (GeneralSecurityException e) {
            throw new BusinessException("号码签名生成失败");
        }
    }

    /**
     * 校验一条记录的签名是否自洽，供客服验真与对账脚本使用
     */
    public boolean verify(LotteryRecord record) {
        if (record == null || record.getSecuritySign() == null) {
            return false;
        }
        String expected = sign(record.getLotteryCode(), record.getIssueNo(),
                record.getSequenceNo(), record.getTicketNumber(), record.getMemberName());
        return expected.equals(record.getSecuritySign());
    }
}
