package solvela.member.register;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import solvela.base.module.redis.RedisService;
import solvela.base.util.SolvelaStringUtil;
import solvela.crypto.PasswordCipher;
import solvela.crypto.PiiCipher;
import solvela.crypto.PiiHasher;
import solvela.enums.GenderEnum;
import solvela.enums.MemberStatusEnum;
import solvela.member.api.MemberIdentity;
import solvela.member.api.MemberPasswordPolicy;
import solvela.member.api.MemberRegisterCmd;
import solvela.member.api.MemberRegisterResult;
import solvela.member.api.RegisterFailReason;
import solvela.member.id.MemberIdAllocator;
import solvela.member.util.MemberPhoneUtil;

/**
 * 会员注册：手机号 + 密码建号，仅此而已。
 *
 * <h3>职责边界：这里没有令牌，也没有验证码</h3>
 * 与 {@code MemberAuthService} 同一个划法 —— 本服务回答「能不能给这个手机号建一个会员，
 * 建好了他是谁」。令牌怎么签是接入层的事。
 *
 * <h3>🔴 目前没有短信验证码，这意味着什么</h3>
 * 全仓没有任何短信基础设施。所以现在<b>任何人都能拿别人的手机号注册</b>，
 * 而 {@code uk_mbr_phone_hash} 是唯一约束 —— 号被占了，真机主就注册不了了。
 *
 * <p>唯一的缓解是 {@link MemberRegisterProperties} 的 IP 限频，它只能压低速率，
 * <b>拦不住定向占号</b>。上线前必须补验证码：加一步「校验验证码」放在本方法最前面，
 * 其余逻辑一行不用动。
 *
 * <p>这段话写在代码里而不是只写在文档里，是因为文档不会在有人 review 这个方法时出现。
 *
 * <h3>账号与昵称自动生成</h3>
 * {@code member_name} 由会员号派生（{@code sv} + 10 位会员号），满足 DDL 的
 * 「字母开头 6~20 位 {@code [A-Za-z][A-Za-z0-9_-]}」且天然唯一 —— 会员号本身唯一。
 * 昵称给一个可读的默认值，两者用户之后都能改（DDL 注释：「用户可改」）。
 *
 * <p>不让用户在注册时自己起账号，是因为那要多一轮「已被占用」的往返，
 * 而此刻他要的只是进去。微信也是这个做法。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MemberRegisterService {

    /** 会员号派生账号的前缀。字母开头是 DDL 的硬要求，不能改成数字开头 */
    private static final String MEMBER_NAME_PREFIX = "sv";

    private static final String DEFAULT_NICKNAME_PREFIX = "会员";

    private static final String DEFAULT_REGISTER_SOURCE = "UNKNOWN";

    private static final String RATE_LIMIT_KEY_PREFIX = "member:register:ip";

    private final MemberRegisterDao memberRegisterDao;
    private final MemberIdAllocator memberIdAllocator;
    private final MemberRegisterProperties properties;
    private final RedisService redisService;
    private final PiiHasher piiHasher;
    private final PiiCipher piiCipher;

    /**
     * 注册。
     *
     * <p>分支顺序有讲究：<b>限频在查重之前</b>。反过来的话，「这个号注册过没有」
     * 这个问题可以无限次免费提问 —— 而那正是 {@code PHONE_TAKEN} 藏不掉的那个枚举口子。
     *
     * <p>格式与强度校验在限频之前：它们不查库、不泄露任何信息，
     * 让一个手滑打错格式的用户去消耗限频额度没有道理。
     */
    @Transactional(rollbackFor = Exception.class)
    public MemberRegisterResult register(MemberRegisterCmd cmd) {

        // ---------- 手机号规范化 ----------
        // 必须先规范化再算摘要：同一个人写成 "138 0000 0000" 和 "13800000000"
        // 会得到两个不同的 hash，唯一约束拦不住，一个号能注册出两个账号
        String phone = MemberPhoneUtil.normalize(cmd.phone());
        if (phone == null) {
            return MemberRegisterResult.fail(RegisterFailReason.BAD_PHONE_FORMAT);
        }

        // ---------- 密码强度 ----------
        if (!MemberPasswordPolicy.isValid(cmd.password())) {
            return MemberRegisterResult.fail(RegisterFailReason.WEAK_PASSWORD);
        }

        // ---------- IP 限频 ----------
        long retryAfter = consumeAttempt(cmd.clientIp());
        if (retryAfter > 0) {
            return MemberRegisterResult.tooManyAttempts(retryAfter);
        }

        // ---------- 手机号查重 ----------
        // 只是提前给一句人话，不是并发防线 —— 真正的防线是下面的唯一约束
        String phoneHashHex = piiHasher.hash(phone);
        if (memberRegisterDao.countByPhoneHash(phoneHashHex) > 0) {
            return MemberRegisterResult.fail(RegisterFailReason.PHONE_TAKEN);
        }

        // ---------- 建号 ----------
        long memberId = memberIdAllocator.nextMemberId();
        String memberName = MEMBER_NAME_PREFIX + memberId;
        String nickname = DEFAULT_NICKNAME_PREFIX + memberId;
        String registerSource = SolvelaStringUtil.isEmpty(cmd.registerSource())
                ? DEFAULT_REGISTER_SOURCE
                : cmd.registerSource();

        try {
            memberRegisterDao.insertMember(
                    memberId,
                    memberName,
                    nickname,
                    GenderEnum.UNKNOWN.getValue(),
                    // 密文与摘要必须来自【同一个】规范化后的字符串，
                    // 否则「解密出来的号」和「能登录的号」会是两个东西
                    piiCipher.encrypt(phone),
                    phoneHashHex,
                    PasswordCipher.encode(cmd.password()),
                    MemberStatusEnum.NORMAL.getValue(),
                    registerSource,
                    cmd.clientIp());
        } catch (DuplicateKeyException e) {
            // 闭合上面那个查重窗口：两个请求同时注册同一个号时，
            // 一个成功一个撞唯一约束。撞了就是「已被注册」，对用户是同一件事。
            // 🔴 别把它当成意外抛出去 —— 那会变成 500，而这是一个完全预期内的结果
            log.info("【会员注册】手机号并发重复注册，已被唯一约束拦下, memberId: {}", memberId);
            return MemberRegisterResult.fail(RegisterFailReason.PHONE_TAKEN);
        }

        // 刻意【不】写 t_member_login_log：注册这件事已经完整记在
        // t_member 的 create_time / register_ip / register_source 三列上，
        // 那正是 DDL 给它们的用途。再写一条 LOGIN_SUCCESS 只是让登录轨迹里多一条
        // 语义不同的行，查一个人「什么时候登过」时反而要先把它剔掉。
        log.info("【会员注册】成功, memberId: {}, source: {}, ip: {}", memberId, registerSource, cmd.clientIp());

        return MemberRegisterResult.ok(new MemberIdentity(
                memberId, memberName, nickname, null, GenderEnum.UNKNOWN));
    }

    /**
     * 消耗一次 IP 配额。返回 0 表示放行，正数表示还要等多少秒。
     *
     * <p>拿不到 IP 时<b>放行并打警告</b>，不是静默放行也不是一律拒绝：
     * 一律拒绝会让任何一次取 IP 失败变成「全站注册不可用」，而那种故障
     * 比放过几个注册严重得多。警告日志让这件事至少能被发现。
     */
    private long consumeAttempt(String clientIp) {
        if (SolvelaStringUtil.isEmpty(clientIp)) {
            log.warn("【会员注册】拿不到客户端 IP，本次注册【未受限频保护】");
            return 0L;
        }
        String key = redisService.generateRedisKey(RATE_LIMIT_KEY_PREFIX, clientIp);
        long attempts = redisService.increment(key, properties.getWindow().toSeconds());
        if (attempts <= properties.getMaxAttemptsPerIp()) {
            return 0L;
        }
        // 已经超了，告诉调用方还要等多久 —— 让用户点第二次才知道被限，是投诉的主要来源
        long ttl = redisService.getExpire(key);
        return Math.max(1L, ttl);
    }
}
