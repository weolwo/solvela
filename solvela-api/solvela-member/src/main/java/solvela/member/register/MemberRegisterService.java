package solvela.member.register;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import solvela.base.event.BizEventPublisher;
import solvela.base.module.redis.RedisService;
import solvela.base.util.SolvelaStringUtil;
import solvela.event.BizActionCodes;
import solvela.event.BizActionEvent;
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
import solvela.member.device.DeviceGuard;
import solvela.member.device.DeviceGuardVerdict;
import solvela.member.api.EmailCodeScene;
import solvela.member.api.EmailCodeVerifyResult;
import solvela.member.api.MemberRegisterType;
import solvela.member.email.MemberEmailCodeService;
import solvela.member.api.SmsCodeVerifyResult;
import solvela.member.sms.MemberSmsCodeService;
import solvela.member.api.SmsScene;
import solvela.member.util.MemberEmailUtil;
import solvela.member.util.MemberPhoneUtil;

/**
 * 会员注册：手机号 + 密码建号，仅此而已。
 *
 * <h3>职责边界：这里没有令牌，也没有验证码</h3>
 * 与 {@code MemberAuthService} 同一个划法 —— 本服务回答「能不能给这个手机号建一个会员，
 * 建好了他是谁」。令牌怎么签是接入层的事。
 *
 * <h3>手机号注册的验证码（2026-09-10 补上）</h3>
 * 这个方法上曾经挂着一整段「全仓没有短信基础设施，所以任何人都能拿别人的手机号注册」——
 * 那段话挂了一个月。现在校验就在下面，与邮箱那条<b>并排放在同一节里</b>：
 * 限频<b>之后</b>、查重<b>之前</b>。那个位置不是随手放的，两个边界各有理由，
 * 写在那一节的注释里。
 *
 * <p>⚠️ 但它<b>受一个开关控制</b>：{@link MemberRegisterProperties#isPhoneCodeRequired()}。
 * 短信服务商还没接上（见 {@code UnavailableSmsSender}），dev / test 靠
 * {@code sms-transport: LOG} 把码打进日志跑通链路；生产在接厂商之前若要放行注册，
 * 得把那个开关按成 false —— <b>而那是一个需要有人明确决定的事</b>，
 * 不再是一个谁也没注意到的现状。
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
    private final DeviceGuard deviceGuard;
    private final MemberEmailCodeService emailCodeService;
    private final MemberSmsCodeService smsCodeService;
    /**
     * 业务动作广播。本域只负责说「有人注册成功了」—— 谁关心谁订阅。
     *
     * <p>🔴 注意这里注入的<b>不是</b>任务引擎：{@code solvela-member} 排在
     * {@code solvela-marketing} 之前，物理上引用不到它（写反了 Maven 直接报循环依赖）。
     * 翻译成任务事件是营销侧防腐层（{@code BizActionEventListener}）的活。
     */
    private final BizEventPublisher bizEventPublisher;

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

        MemberRegisterType registerType = cmd.registerType() == null
                ? MemberRegisterType.PHONE_PASSWORD
                : cmd.registerType();
        boolean byEmail = registerType == MemberRegisterType.EMAIL_CODE;

        // ---------- 身份规范化 ----------
        // 必须先规范化再算摘要：同一个人写成 "138 0000 0000" 和 "13800000000"、
        // 或者 "A@Example.com" 和 "A@example.com"，都会得到两个不同的 hash，
        // 唯一约束拦不住，一个身份能注册出两个账号
        String identity = byEmail
                ? MemberEmailUtil.normalize(cmd.identity())
                : MemberPhoneUtil.normalize(cmd.identity());
        if (identity == null) {
            return MemberRegisterResult.fail(byEmail
                    ? RegisterFailReason.BAD_EMAIL_FORMAT
                    : RegisterFailReason.BAD_PHONE_FORMAT);
        }

        // ---------- 密码强度 ----------
        // 🔴 邮箱注册允许不设密码（t_member.password 允许 NULL，DDL 注释写着
        //    「验证码登录可为空」），那种会员之后走 EMAIL_CODE 登录。
        //    但只要填了就得过强度校验 —— 「填了一个弱密码却被静默接受」比不让填更糟
        boolean hasPassword = !SolvelaStringUtil.isEmpty(cmd.password());
        if (!byEmail || hasPassword) {
            if (!MemberPasswordPolicy.isValid(cmd.password())) {
                return MemberRegisterResult.fail(RegisterFailReason.WEAK_PASSWORD);
            }
        }

        // ---------- 设备限频 ----------
        // 与下面的 IP 限频是【两个维度】，都要过：IP 走代理池就换，
        // 而设备号得先过一次签发限频才拿得到 —— 后者贵得多。
        // 排在 IP 之前：设备维度更硬，先用硬的那把筛
        DeviceGuardVerdict deviceVerdict = deviceGuard.checkRegister(cmd.deviceId());
        if (!deviceVerdict.allowed()) {
            return MemberRegisterResult.deviceLimited(deviceVerdict.retryAfterSeconds());
        }

        // ---------- IP 限频 ----------
        long retryAfter = consumeAttempt(cmd.clientIp());
        if (retryAfter > 0) {
            return MemberRegisterResult.tooManyAttempts(retryAfter);
        }

        // ---------- 验证码 ----------
        // 🔴 两条通道各验各的，都排在【查重之前】：不然一个没有验证码的人也能拿注册接口
        //    反复问「这个身份注册过没有」—— 而 EMAIL_TAKEN / PHONE_TAKEN 是必须
        //    如实回答的（藏了用户就没法用，见 RegisterFailReason 类注释），
        //    那个枚举口子只能靠「先证明你拥有这个身份」把成本抬上去。
        //
        // 而它排在限频【之后】：验码要读 Redis、还要写回失败计数，
        // 让一个已经被限频挡下的请求去做这些事没有意义 —— 便宜的筛子先用
        if (!byEmail && properties.isPhoneCodeRequired()) {
            SmsCodeVerifyResult codeResult =
                    smsCodeService.verify(SmsScene.REGISTER, identity, cmd.smsCode());
            if (codeResult != SmsCodeVerifyResult.OK) {
                return MemberRegisterResult.fail(switch (codeResult) {
                    case NOT_FOUND -> RegisterFailReason.SMS_CODE_EXPIRED;
                    case MISMATCH -> RegisterFailReason.SMS_CODE_MISMATCH;
                    case TOO_MANY_ATTEMPTS -> RegisterFailReason.SMS_CODE_LOCKED;
                    case OK -> throw new IllegalStateException("不可能走到：OK 已在上面判掉");
                });
            }
        }
        if (byEmail) {
            EmailCodeVerifyResult codeResult =
                    emailCodeService.verify(EmailCodeScene.REGISTER, identity, cmd.emailCode());
            if (codeResult != EmailCodeVerifyResult.OK) {
                return MemberRegisterResult.fail(switch (codeResult) {
                    case NOT_FOUND -> RegisterFailReason.EMAIL_CODE_EXPIRED;
                    case MISMATCH -> RegisterFailReason.EMAIL_CODE_MISMATCH;
                    case TOO_MANY_ATTEMPTS -> RegisterFailReason.EMAIL_CODE_LOCKED;
                    case OK -> throw new IllegalStateException("不可能走到：OK 已在上面判掉");
                });
            }
        }

        // ---------- 身份查重 ----------
        // 只是提前给一句人话，不是并发防线 —— 真正的防线是下面的唯一约束
        String identityHashHex = piiHasher.hash(identity);
        int taken = byEmail
                ? memberRegisterDao.countByEmailHash(identityHashHex)
                : memberRegisterDao.countByPhoneHash(identityHashHex);
        if (taken > 0) {
            return MemberRegisterResult.fail(byEmail
                    ? RegisterFailReason.EMAIL_TAKEN
                    : RegisterFailReason.PHONE_TAKEN);
        }

        // ---------- 建号 ----------
        return createMember(cmd, byEmail, identity, identityHashHex);
    }

    /**
     * 真正建号。返回结果而不是抛异常，理由见 {@link RegisterFailReason}。
     *
     * <p>刻意<b>不</b>写 {@code t_member_login_log}：注册这件事已经完整记在 t_member 的
     * {@code create_time / register_ip / register_source} 三列上，那正是 DDL 给它们的用途。
     * 再写一条 LOGIN_SUCCESS 只是让登录轨迹里多一条语义不同的行，
     * 查一个人「什么时候登过」时反而要先把它剔掉。
     */
    private MemberRegisterResult createMember(MemberRegisterCmd cmd, boolean byEmail,
                                              String identity, String identityHashHex) {
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
                    // 否则「解密出来的号」和「能登录的号」会是两个东西。
                    // 🔴 手机号与邮箱只填一半，另一半是 null —— 两列都允许 NULL，
                    //    而 UNHEX(NULL) 就是 NULL，不用特判
                    byEmail ? null : piiCipher.encrypt(identity),
                    byEmail ? null : identityHashHex,
                    byEmail ? piiCipher.encrypt(identity) : null,
                    byEmail ? identityHashHex : null,
                    // 邮箱注册可以不设密码，那种会员之后走验证码登录。
                    // encode(null) 会抛，所以这里必须先判 —— 而「密码列为 NULL」
                    // 正是 DDL 给验证码注册留的口子
                    SolvelaStringUtil.isEmpty(cmd.password()) ? null : PasswordCipher.encode(cmd.password()),
                    MemberStatusEnum.NORMAL.getValue(),
                    registerSource,
                    cmd.clientIp());
        } catch (DuplicateKeyException e) {
            // 闭合查重与插入之间那个窗口：两个请求同时注册同一个号时，
            // 一个成功一个撞唯一约束。撞了就是「已被注册」，对用户是同一件事。
            // 🔴 别把它当成意外抛出去 —— 那会变成 500，而这是一个完全预期内的结果
            log.info("【会员注册】并发重复注册，已被唯一约束拦下, byEmail: {}, memberId: {}", byEmail, memberId);
            return MemberRegisterResult.fail(byEmail
                    ? RegisterFailReason.EMAIL_TAKEN
                    : RegisterFailReason.PHONE_TAKEN);
        }

        /*
         * 广播「注册成功」。
         *
         * 🔴 必须在【本方法的事务内】发：接住它的监听器挂在 AFTER_COMMIT 上，
         *    注册事务回滚时这个事件就不会投递 —— 所以不存在
         *    「号没建成但新人任务已经发了奖」。
         *    挪到事务外的后果不是收不到（监听器开了 fallbackExecution），
         *    而是【收得太早】：建号失败回滚了，奖却已经发出去了。
         *
         * 幂等键用 memberId：一个会员一辈子只注册一次，天然唯一。
         * 不传的话服务端会按「事件自然日」兜底，那对注册来说恰好也不会出错，
         * 但那是运气 —— 依赖兜底规则和依赖一个真单号是两回事。
         */
        bizEventPublisher.publish(BizActionEvent.of(
                BizActionCodes.MEMBER_REGISTER, memberId, String.valueOf(memberId)));

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
