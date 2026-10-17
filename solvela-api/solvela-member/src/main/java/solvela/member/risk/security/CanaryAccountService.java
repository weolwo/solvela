package solvela.member.risk.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import solvela.crypto.PasswordCipher;
import solvela.crypto.PiiCipher;
import solvela.crypto.PiiHasher;
import solvela.enums.GenderEnum;
import solvela.enums.MemberStatusEnum;
import solvela.member.id.MemberIdAllocator;
import solvela.member.register.MemberRegisterDao;
import solvela.member.util.MemberEmailUtil;

import java.security.SecureRandom;
import java.util.Base64;

/**
 * 建蜜罐账号（网关方案 §5.1）：库里真实存在、但<b>从不对外使用</b>的账号。有人拿它的邮箱来登录，
 * 说明他手里有我们的用户名单 —— 这是唯一能发现「用户库泄露了」的手段。
 *
 * <h3>🔴 蜜罐邮箱不进代码、不进种子数据</h3>
 * 写进 git 的话，能看仓库的人就都知道哪些是蜜罐，等于自废。建几个、放在哪（哪些「可能泄露」的地方）由运营定，
 * 上线前通过后台这个接口自己建（2026-10-17 定）。
 *
 * <h3>建出来的样子</h3>
 * <ul>
 *   <li>{@code register_source = CANARY}：所有会员列表默认排除它（{@code MemberMapper.xml}），统计口径里也就没有它；</li>
 *   <li>密码是一串随机字节的哈希，原文当场丢弃 —— <b>谁也登不进去</b>，所以对它的每一次登录都落在「账号或密码错误」，
 *       与真实账号输错密码逐字节相同、耗时相同（同样跑一次 Argon2）；</li>
 *   <li>状态正常：冻结的话回答就变成「账号状态异常」，一眼看出它和别的号不一样；</li>
 *   <li>不发注册事件、不进新人任务、不发券：它不是一个人。</li>
 * </ul>
 * 识别与处罚在登录风控的 {@code CANARY_ACCOUNT} 规则与 {@link CanaryTrap} 里。
 *
 * @Date 2026-10-17
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CanaryAccountService {

    /** 蜜罐账号的注册来源 */
    public static final String REGISTER_SOURCE = "CANARY";

    private static final SecureRandom RANDOM = new SecureRandom();

    private final MemberRegisterDao memberRegisterDao;

    private final MemberIdAllocator memberIdAllocator;

    private final PiiHasher piiHasher;

    private final PiiCipher piiCipher;

    /**
     * 用这个邮箱建一个蜜罐账号。
     *
     * @return 新会员号
     * @throws IllegalArgumentException 邮箱格式不对，或这个邮箱已经是一个会员（不能把真人的号变成蜜罐）
     */
    public long create(String email, String operator) {
        String normalized = MemberEmailUtil.normalize(email);
        if (normalized == null) {
            throw new IllegalArgumentException("邮箱格式不对: " + email);
        }
        String hashHex = piiHasher.hash(normalized);
        if (memberRegisterDao.countByEmailHash(hashHex) > 0) {
            throw new IllegalArgumentException("这个邮箱已经是会员了 —— 蜜罐必须是从没被用过的邮箱");
        }
        long memberId = memberIdAllocator.nextMemberId();
        byte[] secret = new byte[32];
        RANDOM.nextBytes(secret);
        try {
            // 账号名、昵称与自主注册的一模一样（MemberRegisterService.createMember）：长得不一样的蜜罐，一眼就认得出来
            memberRegisterDao.insertMember(memberId, "sv" + memberId, "会员" + memberId, GenderEnum.UNKNOWN.getValue(),
                    null, null, piiCipher.encrypt(normalized), hashHex,
                    // 原文不落任何地方：这个号谁也登不进去
                    PasswordCipher.encode(Base64.getEncoder().encodeToString(secret)),
                    MemberStatusEnum.NORMAL.getValue(), REGISTER_SOURCE, null);
        } catch (DuplicateKeyException e) {
            throw new IllegalArgumentException("这个邮箱已经是会员了 —— 蜜罐必须是从没被用过的邮箱");
        }
        log.warn("【蜜罐账号】新建, memberId: {}, 操作人: {}", memberId, operator);
        return memberId;
    }
}
