package solvela.member.register;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import solvela.base.event.BizEventPublisher;
import solvela.base.util.SolvelaStringUtil;
import solvela.event.BizActionCodes;
import solvela.event.BizActionEvent;
import solvela.crypto.PasswordCipher;
import solvela.crypto.PiiCipher;
import solvela.crypto.PiiHasher;
import solvela.enums.GenderEnum;
import solvela.enums.MemberRiskDecisionEnum;
import solvela.enums.MemberRiskEventTypeEnum;
import solvela.enums.MemberRiskOutcomeEnum;
import solvela.enums.MemberStatusEnum;
import solvela.member.api.MemberIdentity;
import solvela.member.api.MemberLoginType;
import solvela.member.api.MemberPasswordPolicy;
import solvela.member.api.MemberRegisterCmd;
import solvela.member.api.MemberRegisterResult;
import solvela.member.api.RegisterFailReason;
import solvela.member.id.MemberIdAllocator;
import solvela.member.risk.MemberRiskEngine;
import solvela.member.risk.RiskAction;
import solvela.member.risk.RiskContext;
import solvela.member.risk.RiskDecision;
import solvela.member.risk.RiskSession;
import solvela.member.auth.NewDeviceLoginNotifier;
import solvela.member.device.DeviceDispositionService;
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
 * <h3>限频在 2026-10-16 整体搬进了风控引擎</h3>
 * 这里原来有两道手写的闸：{@code deviceGuard.checkRegister}（一台设备一天 2 个号）
 * 和 {@code consumeAttempt}（一个 IP 一小时 10 次）。现在它们是四条规则
 * （{@code REG_IP_LIMIT} / {@code REG_DEVICE_LIMIT} / {@code REG_IP_BLACKLIST} /
 * {@code REG_DEVICE_BANNED}），阈值在 {@code t_member_risk_rule}，<b>每一次注册尝试都有一行流水</b>。
 *
 * <p>换来的不只是「阈值能在后台改」：注册这条链路以前<b>完全看不见</b> ——
 * 批量建号发生过没有、挡下了多少、挡错了多少，一个数都没有，
 * 唯一的痕迹是 {@code t_member.register_ip} 和一行 WARN 日志。
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

    private final MemberRegisterDao memberRegisterDao;
    private final MemberIdAllocator memberIdAllocator;
    private final MemberRegisterProperties properties;
    private final PiiHasher piiHasher;
    private final PiiCipher piiCipher;
    /** 注册的四道闸全在它后面。限频、黑名单、设备封禁都是规则，不再是本类里的 if */
    private final MemberRiskEngine riskEngine;
    /** 只为执行 {@link RiskAction#OBSERVE_DEVICE}：一台建号超限的设备，下次登录要多验一道码 */
    private final DeviceDispositionService dispositionService;
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
    private final NewDeviceLoginNotifier newDeviceLoginNotifier;

    /**
     * 注册。
     *
     * <p>分支顺序有讲究：<b>风控在查重之前</b>。反过来的话，「这个号注册过没有」
     * 这个问题可以无限次免费提问 —— 而那正是 {@code PHONE_TAKEN} 藏不掉的那个枚举口子。
     *
     * <p>格式与强度校验在风控<b>之前</b>，而且<b>连账本都不开</b>：它们不查库、不泄露任何信息，
     * 而开了账本就会跑规则 —— 那几条规则「这一次请求本身就算一次」，
     * 于是一个手滑打错格式的人会白白消耗掉他今天的注册额度。
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

        // ---------- 身份摘要 ----------
        // 🔴 挪到了风控【之前】（原来在查重那一步才算）：流水那一行要按身份摘要归集
        //    （「同一个号被试了多少次」是注册这边唯一藏不掉的枚举口子）。
        //    代价是零 —— 它只是一次本地 HMAC，不查库
        String identityHashHex = piiHasher.hash(identity);

        try (RiskSession risk = riskEngine.open(riskContext(registerType, identity, identityHashHex, cmd))) {
            return insideRisk(risk, cmd, byEmail, identity, identityHashHex);
        }
    }

    /**
     * 风控账本里的那一段：前置决策 + 验证码 + 查重 + 建号。
     *
     * <h3>🔴 每一条返回路径都走 {@code risk.end(...)}</h3>
     * 写成这样，漏掉一条返回路径就漏掉一行流水，而那是 code review 看得见的
     * （见 {@code RiskSession} 的类注释）。
     *
     * <p>⚠️ 本方法在 {@code register} 的<b>事务里</b>，所以流水那一行也在同一个事务里 ——
     * 主干抛未预料异常时（对用户是 500），{@code close()} 兜底写的那行
     * {@code INTERNAL_ERROR} 会随事务一起回滚。
     * 接受它：那一刻真正要查的东西（traceId、异常栈）在日志里，
     * 而为了这一行把风控流水拆到独立事务里，代价是注册主干多一次连接往返。
     */
    private MemberRegisterResult insideRisk(RiskSession risk, MemberRegisterCmd cmd, boolean byEmail,
                                            String identity, String identityHashHex) {

        // ---------- 前置决策 ----------
        // 四条规则都在这一步跑完：IP 黑名单、设备封禁（都是 DENY），IP 与设备的分级限流。
        // 注册只有这一个决策点 —— 验证码验过账号就建出来了，没有「验完再判一次」的位置
        MemberRegisterResult blocked = runPreDecision(risk, cmd);
        if (blocked != null) {
            return blocked;
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
                return risk.end(MemberRiskOutcomeEnum.BAD_CREDENTIALS,
                        MemberRegisterResult.fail(switch (codeResult) {
                            case NOT_FOUND -> RegisterFailReason.SMS_CODE_EXPIRED;
                            case MISMATCH -> RegisterFailReason.SMS_CODE_MISMATCH;
                            case TOO_MANY_ATTEMPTS -> RegisterFailReason.SMS_CODE_LOCKED;
                            case OK -> throw new IllegalStateException("不可能走到：OK 已在上面判掉");
                        }));
            }
        }
        if (byEmail) {
            EmailCodeVerifyResult codeResult =
                    emailCodeService.verify(EmailCodeScene.REGISTER, identity, cmd.emailCode());
            if (codeResult != EmailCodeVerifyResult.OK) {
                return risk.end(MemberRiskOutcomeEnum.BAD_CREDENTIALS,
                        MemberRegisterResult.fail(switch (codeResult) {
                            case NOT_FOUND -> RegisterFailReason.EMAIL_CODE_EXPIRED;
                            case MISMATCH -> RegisterFailReason.EMAIL_CODE_MISMATCH;
                            case TOO_MANY_ATTEMPTS -> RegisterFailReason.EMAIL_CODE_LOCKED;
                            case OK -> throw new IllegalStateException("不可能走到：OK 已在上面判掉");
                        }));
            }
        }

        // ---------- 身份查重 ----------
        // 只是提前给一句人话，不是并发防线 —— 真正的防线是下面的唯一约束
        int taken = byEmail
                ? memberRegisterDao.countByEmailHash(identityHashHex)
                : memberRegisterDao.countByPhoneHash(identityHashHex);
        if (taken > 0) {
            return risk.end(MemberRiskOutcomeEnum.IDENTITY_TAKEN, MemberRegisterResult.fail(byEmail
                    ? RegisterFailReason.EMAIL_TAKEN
                    : RegisterFailReason.PHONE_TAKEN));
        }

        // ---------- 建号 ----------
        // createMember 只有一种失败：撞唯一约束，也就是「已被注册」。
        // 由结果反推结局，而不是在 createMember 里再写一次 risk.end —— 账本的边界留在本方法里
        MemberRegisterResult created = createMember(cmd, byEmail, identity, identityHashHex);
        return risk.end(created.success()
                ? MemberRiskOutcomeEnum.SUCCESS
                : MemberRiskOutcomeEnum.IDENTITY_TAKEN, created);
    }

    /**
     * 前置决策：被挡下就返回要给调用方的东西，放行返回 null。
     *
     * <h3>🔴 限流与拒绝对外是<b>同一句</b></h3>
     * 告诉对方「你这个 IP 被拉黑了」「你这台设备被封了」等于给他一个精确的调试反馈，
     * 他换一个就继续 —— 与登录那边同一个判据（方案 §6.4）。
     *
     * <p>这也是本次改造<b>唯一一处对外行为的变化</b>：原来设备维度被限回的是
     * {@code DEVICE_LIMITED}（「当前设备操作过于频繁」），现在两维合成一句
     * 「操作过于频繁」。差别只在措辞 —— 而那句话原本正好告诉了批量注册的人该换设备还是换代理。
     */
    private MemberRegisterResult runPreDecision(RiskSession risk, MemberRegisterCmd cmd) {
        RiskDecision pre = riskEngine.evaluatePre(risk);
        applyActions(pre, cmd.deviceId());
        if (pre.atLeast(MemberRiskDecisionEnum.LIMIT)) {
            return risk.end(MemberRiskOutcomeEnum.PRE_BLOCKED,
                    MemberRegisterResult.tooManyAttempts(pre.retryAfterSeconds()));
        }
        if (!pre.is(MemberRiskDecisionEnum.PASS)) {
            /*
             * 🔴 走到这里说明有人把一条注册规则配成了滑块 / OTP / 冷却 —— 而注册这条链路
             * 执行不了它们（滑块票在发码那一步核销，注册本身已经验过一次码）。
             *
             * 放行并吵得够响：既不能静默（那等于这条规则不存在），也不能当成拦截
             * （把一个配置错误变成「全站注册不可用」，代价完全不对称）。
             * 正常情况下进不来 —— 分级档位由规则自己的 validate 拦、固定处置由
             * RiskRuleRegistry.toConfig 拦，这里是最后一层。
             */
            log.error("【会员注册】风控给出了注册场景执行不了的处置: {}，本次放行 —— 请检查规则配置",
                    pre.decision());
        }
        return null;
    }

    /**
     * 执行生效结论带的动作。注册这边只可能有一个：把设备降进观察档。
     *
     * <p>另外两个动作在注册场景不会出现 —— {@code RECORD_LIMIT} 是密码阶梯冷却专有的，
     * {@code NOTIFY_OWNER} 要有「主人」而注册时还没有这个人。
     */
    private void applyActions(RiskDecision decision, String deviceId) {
        if (decision.has(RiskAction.OBSERVE_DEVICE) && deviceId != null) {
            dispositionService.observe(deviceId, "风控命中：本设备建号过多");
        }
    }

    /**
     * 组装注册场景的规则上下文。
     *
     * <h3>🔴 member 恒为 null，这不是遗漏</h3>
     * 注册的全部意义就是这个人还没有账号。所以用到 {@code ctx.getMember()} 的规则
     * （矩阵、画像、密码冷却）在注册场景<b>一条都没有</b>，而不是「拿 null 当最严值」。
     *
     * <p>{@code captchaPassed} 恒为 false：注册接口收不到滑块票 ——
     * 票由网关在<b>发码</b>那一步核销。
     */
    private RiskContext riskContext(MemberRegisterType registerType, String identity,
                                    String identityHashHex, MemberRegisterCmd cmd) {
        return new RiskContext(MemberRiskEventTypeEnum.REGISTER, loginTypeOf(registerType),
                identity, identityHashHex, null,
                cmd.deviceId(), cmd.deviceType(), cmd.clientIp(), false);
    }

    /**
     * 注册方式 → 流水里的 {@code login_type}。
     *
     * <p>两套枚举不合并（见 {@link MemberRegisterType} 的类注释），但流水那一列记的是
     * 「这是手机号还是邮箱」—— 注册与登录在这件事上是同一个问题，所以复用同一列，
     * 让看板不必为注册再分一套口径。
     */
    private static MemberLoginType loginTypeOf(MemberRegisterType registerType) {
        return registerType == MemberRegisterType.EMAIL_CODE
                ? MemberLoginType.EMAIL_CODE
                : MemberLoginType.PHONE_PASSWORD;
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

        // 注册即登录、但不写登录日志 —— 新设备提醒要靠这条认出「注册那台」，见 NewDeviceLoginNotifier
        newDeviceLoginNotifier.rememberRegistrationDevice(memberId, cmd.deviceId());

        log.info("【会员注册】成功, memberId: {}, source: {}, ip: {}", memberId, registerSource, cmd.clientIp());
        return MemberRegisterResult.ok(new MemberIdentity(
                memberId, memberName, nickname, null, GenderEnum.UNKNOWN));
    }
}
