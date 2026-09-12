package solvela.member.sms;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import solvela.base.domain.SystemEnvironment;
import solvela.crypto.PiiHasher;
import solvela.member.api.SmsCodeFailReason;
import solvela.member.api.SmsScene;
import solvela.member.api.SmsCodeSendResult;
import solvela.member.api.SmsCodeVerifyResult;
import solvela.member.api.SmsDelivery;
import solvela.member.code.CodeIssueOutcome;
import solvela.member.code.CodeVerifyOutcome;
import solvela.member.code.VerificationCodeProperties;
import solvela.member.code.VerificationCodeStore;
import solvela.member.util.MemberPhoneUtil;

/**
 * 短信验证码：<b>只管手机号那一半</b>。
 *
 * <h3>与 {@link VerificationCodeStore} 的分工</h3>
 * 生成、冷却、日限、验错计数、消费 —— 那些<b>安全攸关且与通道无关</b>的性质
 * 全在 store 里，与邮箱共用<b>同一份代码</b>。本类只负责三件手机号自己的事：
 * <b>规范化号码</b>、<b>调发送器</b>、<b>翻译成短信那套原因枚举</b>。
 *
 * <p>结构与 {@code MemberEmailCodeService} 逐行对称，这是刻意的：
 * 两条通道的行为差异只应该来自它们<b>真正不同</b>的地方（模板、发送器、成本），
 * 而不是来自「当时谁写的、写的时候想到没想到」。
 *
 * <h3>与邮箱唯一实质不同的一点：成本</h3>
 * 邮件不要钱，短信一条几分钱 —— 被刷一天就是实打实的账单。所以
 * {@code max-sms-send-per-ip-per-day} 比邮件那档紧得多（5 vs 20）。
 *
 * @Date 2026-09-10
 */
@Slf4j
@Service
public class MemberSmsCodeService {

    /** 进 Redis key 的通道标识。与邮箱的码互不相干。 */
    private static final String CHANNEL = "sms";

    private final VerificationCodeStore codeStore;

    private final SmsSender smsSender;

    private final PiiHasher piiHasher;

    private final VerificationCodeProperties properties;

    private final SystemEnvironment systemEnvironment;

    /**
     * 🔴 用 {@code ObjectProvider} 而不是直接注入 {@link SmsSender}：全仓<b>还没有</b>
     * 任何实现，直接注入的话每一个 Spring 上下文都会起不来。
     *
     * <p>也不要退回到「给 UnavailableSmsSender 挂 {@code @ConditionalOnMissingBean}」——
     * 那个组合不成立，代价见 {@link UnavailableSmsSender} 的类注释。
     * {@code ObjectProvider} 没有任何求值顺序问题：有实现就用实现，没有就那堵墙。
     *
     * <p>⚠️ {@code @Autowired} <b>不能省</b>：本类有两个构造，Spring 认不出该用哪个，
     * 表现是 {@code NoSuchMethodException: <init>()} —— 它会去找默认构造。
     */
    @Autowired
    public MemberSmsCodeService(VerificationCodeStore codeStore,
                                ObjectProvider<SmsSender> senderProvider,
                                PiiHasher piiHasher,
                                VerificationCodeProperties properties,
                                SystemEnvironment systemEnvironment) {
        this(codeStore, senderProvider.getIfAvailable(UnavailableSmsSender::new),
                piiHasher, properties, systemEnvironment);
    }

    /** 直接给发送器的构造，供测试用 —— 生产走上面那个。 */
    MemberSmsCodeService(VerificationCodeStore codeStore, SmsSender smsSender, PiiHasher piiHasher,
                         VerificationCodeProperties properties, SystemEnvironment systemEnvironment) {
        this.codeStore = codeStore;
        this.smsSender = smsSender;
        this.piiHasher = piiHasher;
        this.properties = properties;
        this.systemEnvironment = systemEnvironment;
    }

    /**
     * 🔴 生产环境不许用 LOG 通道，<b>启动即失败</b>。理由与邮箱那边一字不差：
     * 日志里躺着每个人的验证码，看得到日志的人就能接管任意账号。
     *
     * <p>短信这边还有一层：LOG 通道下<b>一分钱都不花</b>，
     * 于是「配错了但没人发现」可以持续很久 —— 邮件至少还会有人问「怎么没收到信」，
     * 而短信在 LOG 模式下连账单都不会异常。
     */
    @jakarta.annotation.PostConstruct
    void checkTransport() {
        if (properties.getSmsTransport() != VerificationCodeProperties.Transport.LOG) {
            // 🔴 反过来的那一半：配了 REAL，却根本没接厂商。
            //    不在这里拦的话，它要等到第一个真实用户点「获取验证码」才暴露 ——
            //    表现是一个 500，而那一刻没人在看日志。启动时炸，部署的人当场就知道。
            if (systemEnvironment.isProd() && !smsSender.available()) {
                //『只用邮箱』是一个合法的部署形态，但必须是【说出口的】那种合法。
                // 见 VerificationCodeProperties#allowMissingSmsVendor 的说明。
                if (properties.isAllowMissingSmsVendor()) {
                    log.warn("【短信】没有接入任何服务商，而 allow-missing-sms-vendor=true —— "
                            + "本次启动放行。🔴 手机号注册 / 登录 / 绑定 / 找回密码这几条路"
                            + "现在【走到哪一步都会抛异常】，对用户是 500。"
                            + "请确认 C 端没有暴露任何手机号入口。接好厂商后把这个开关删掉。");
                    return;
                }
                throw new IllegalStateException(
                        "solvela.member.code.sms-transport=REAL，但没有任何 SmsSender 实现："
                                + "短信一条也发不出去，手机号注册 / 登录会全线失败。"
                                + "接好厂商并加一个 @Component SmsSender 实现，再上生产。"
                                + "确实只打算用邮箱的话，显式配 "
                                + "solvela.member.code.allow-missing-sms-vendor=true —— "
                                + "但那条路上的请求仍然会 500，C 端不要暴露手机号入口。");
            }
            return;
        }
        if (systemEnvironment.isProd()) {
            throw new IllegalStateException(
                    "solvela.member.code.sms-transport=LOG 不允许在生产环境使用："
                            + "它会把每个人的验证码打进日志，而看得到日志的人就能接管任意账号。"
                            + "生产请配成 REAL，并实现 SmsSender 接好厂商。");
        }
        log.warn("【短信验证码】当前是 LOG 通道：不发短信，验证码直接打进日志。"
                        + "搜关键字【短信验证码-LOG】。当前环境 {}。"
                        + "🔴 这个开关只允许在非生产环境使用，配到生产会启动失败。",
                systemEnvironment.getCurrentEnvironment());
    }

    /**
     * 发一条验证码短信。
     *
     * <p>分支顺序：<b>格式 → 冷却 → 日限 → 发送</b>。格式校验排最前面，
     * 因为它不查任何存储、不泄露任何信息 —— 让一个手滑打错号码的用户
     * 去消耗当天配额没有道理，而短信那个配额是真花钱的。
     *
     * <p>🔴 发不发由调用方（{@link MemberSmsCodeIssuer}）决定，本方法只执行。
     * SUPPRESS 时<b>码照存、配额照扣</b>，只是不真的发出去 ——
     * 不存的话，攻击者能从「验码返回什么」反推出这个号有没有账号。
     */
    public SmsCodeSendResult send(SmsScene scene, String rawPhone, String clientIp) {
        return send(scene, rawPhone, clientIp, SmsDelivery.DELIVER);
    }

    /** 带投递决定的那个。外部入口一律走 {@link MemberSmsCodeIssuer}，不要直接调这个。 */
    public SmsCodeSendResult send(SmsScene scene, String rawPhone, String clientIp,
                                  SmsDelivery delivery) {
        String phone = MemberPhoneUtil.normalize(rawPhone);
        if (phone == null) {
            return SmsCodeSendResult.fail(SmsCodeFailReason.BAD_PHONE_FORMAT);
        }
        String hash = piiHasher.hash(phone);

        CodeIssueOutcome issued = codeStore.issue(CHANNEL, scene.name(), hash, clientIp);
        if (!issued.ok()) {
            return switch (issued.status()) {
                case TOO_FREQUENT -> SmsCodeSendResult.tooFrequent(issued.retryAfterSeconds());
                case DAILY_LIMIT -> SmsCodeSendResult.dailyLimit(issued.retryAfterSeconds());
                case OK -> throw new IllegalStateException("不可能走到：OK 已在上面判掉");
            };
        }

        if (delivery == SmsDelivery.SUPPRESS) {
            // 码已经存进去了，只是不发。返回成功 —— 与真发出去时一模一样
            return SmsCodeSendResult.ok();
        }

        if (properties.getSmsTransport() == VerificationCodeProperties.Transport.LOG) {
            // 🔴 这里打【完整的号码和完整的码】—— 那就是这个通道的全部用途，
            //    打码就没法用了。生产环境走不到这里（checkTransport 已经拦下）
            log.warn("【短信验证码-LOG】scene={}, phone={}, code={}, 有效期 {} 分钟",
                    scene, phone, issued.code(), properties.ttl().toMinutes());
            return SmsCodeSendResult.ok();
        }

        try {
            smsSender.send(phone, scene, issued.code(), properties.ttl().toMinutes());
        } catch (Exception e) {
            // 🔴 把刚存的码删掉：留着它会让冷却生效，于是用户在收不到短信的同时
            //    还被告知「请稍后再试」
            codeStore.discard(CHANNEL, scene.name(), hash);
            // 打 error 而不是吞掉：这是【我们自己】的问题（没接厂商、余额不足、
            // 签名被限），不是正常的业务失败。日志里只放打码后的号码
            log.error("【短信验证码】发送失败, scene: {}, phone: {}",
                    scene, MemberPhoneUtil.mask(phone), e);
            return SmsCodeSendResult.fail(SmsCodeFailReason.SEND_FAILED);
        }

        log.info("【短信验证码】已发送, scene: {}, phone: {}", scene, MemberPhoneUtil.mask(phone));
        return SmsCodeSendResult.ok();
    }

    /** 校验并<b>消费</b>验证码。通过之后同一个码不能再用第二次。 */
    public SmsCodeVerifyResult verify(SmsScene scene, String rawPhone, String inputCode) {
        String phone = MemberPhoneUtil.normalize(rawPhone);
        if (phone == null) {
            return SmsCodeVerifyResult.NOT_FOUND;
        }
        return toSmsResult(codeStore.verify(CHANNEL, scene.name(), piiHasher.hash(phone), inputCode));
    }

    private static SmsCodeVerifyResult toSmsResult(CodeVerifyOutcome outcome) {
        return switch (outcome) {
            case OK -> SmsCodeVerifyResult.OK;
            case NOT_FOUND -> SmsCodeVerifyResult.NOT_FOUND;
            case MISMATCH -> SmsCodeVerifyResult.MISMATCH;
            case TOO_MANY_ATTEMPTS -> SmsCodeVerifyResult.TOO_MANY_ATTEMPTS;
        };
    }
}
