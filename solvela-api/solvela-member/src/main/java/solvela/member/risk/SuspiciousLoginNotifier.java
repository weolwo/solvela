package solvela.member.risk;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.stereotype.Component;
import solvela.base.mail.MailService;
import solvela.base.mail.MailTemplateCodeEnum;
import solvela.base.module.redis.RedisService;
import solvela.base.util.SolvelaIpUtil;
import solvela.base.util.SolvelaStringUtil;
import solvela.crypto.PiiCipher;
import solvela.enums.NotificationTemplateEnum;
import solvela.member.Member;
import solvela.member.auth.MemberAuthDao;
import solvela.member.email.EmailSendExecutorConfig;
import solvela.notification.domain.NotifyRequest;
import solvela.notification.service.NotificationService;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * {@link RiskAction#NOTIFY_OWNER}：「我们拦下了一次可疑登录」—— 给主人发一封邮件 + 一条站内信。
 *
 * <h3>它和新设备登录提醒不是一回事</h3>
 * 新设备提醒说的是「登进去了，是一台新设备」；这一封说的是「有人想进来，<b>我们没让他直接进去</b>」。
 * 前者是事实通报，后者是「你的密码可能已经泄露了」——
 * 能走到这一步说明对方<b>密码是对的</b>（后置决策排在密码校验之后）。
 *
 * <h3>🔴 为什么它排在 P2，而不是跟着规则一起上</h3>
 * 「可疑到什么程度才值得打扰主人」只能看线上数据定。没有数据就上，第一周会把它发成垃圾邮件，
 * 而用户从中学到的是<b>忽略这类信</b> —— 那比不发更糟，因为真出事的那一封也会被忽略。
 *
 * <p>所以触发面刻意很窄：只有 {@code OTP_STRONG}（要求换一个通道验证）和 {@code DENY}
 * 这两档会带这个动作（见 {@code RiskSession}），而且<b>按会员节流</b>。
 *
 * <h3>🔴 节流不是「省钱」，是「保住这封信的可信度」</h3>
 * 一次撞库会对同一个账号连着试几十次，每一次都可能判到 OTP_STRONG。
 * 不节流的话主人收到三十封一模一样的信，而他第一反应是把发件人拉黑。
 *
 * <h3>🔴 信里不放任何链接</h3>
 * 钓鱼邮件最爱模仿的就是这类「安全提醒」。我们自己的信里一个链接都没有，
 * 用户才有依据判断「带链接的那封是假的」。与新设备提醒同一条规矩。
 *
 * @Date 2026-10-16
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SuspiciousLoginNotifier {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    /** 节流键：一个会员这段时间内只发一封 */
    private static final String KEY_THROTTLE = "mbr:risk:notify-owner:";

    private final MemberAuthDao memberAuthDao;

    private final PiiCipher piiCipher;

    private final MailService mailService;

    private final NotificationService notificationService;

    private final RedisService redisService;

    /** 发信线程池。容器里有多个 AsyncTaskExecutor，按名字精确注入 —— 同 {@code NewDeviceLoginNotifier} */
    @jakarta.annotation.Resource(name = EmailSendExecutorConfig.EMAIL_SEND_EXECUTOR)
    private AsyncTaskExecutor sendExecutor;

    /**
     * 总开关。误发成灾时的回退，不用发版。
     *
     * <p>⚠️ 前缀是 {@code solvela.member.login.*}（挨着新设备登录提醒那个开关），
     * <b>不是</b> {@code solvela.member.risk.*} —— 后者整个归
     * {@code MemberRiskProperties}（{@code @ConfigurationProperties}）管，
     * 在那个前缀下再用 {@code @Value} 塞键，读那个类的人看不到它们。
     *
     * <p>默认 {@code true}：这封信是「密码可能已经泄露」唯一的告知渠道，
     * 默认关着等于把它做了却没人收到。
     */
    @Value("${solvela.member.login.notify-suspicious:true}")
    private boolean enabled;

    /**
     * 同一个会员多久最多一封。
     *
     * <p>6 小时：一次撞库通常在几分钟内打完，6 小时足够把它压成一封；
     * 而真的有人反复尝试了一整天，主人第二封信也该收到。
     */
    @Value("${solvela.member.login.notify-suspicious-throttle:6h}")
    private Duration throttle;

    /**
     * 执行这个动作。<b>任何异常都只记日志</b> —— 通知发不出去不该让一次登录跟着失败。
     *
     * @param memberId   收信的人。查无此人时引擎不会带这个动作，这里再兜一次
     * @param clientIp   只用来解析归属地；<b>不进信里</b>（IP 对用户没有意义）
     * @param deviceType 设备端，拿不到时说「未知设备」
     */
    public void notifyOwner(Long memberId, String clientIp, String deviceType) {
        if (!enabled || memberId == null) {
            return;
        }
        try {
            if (!acquire(memberId)) {
                log.info("【可疑登录】节流中，本次不发, memberId: {}", memberId);
                return;
            }
            String loginTime = LocalDateTime.now().format(TIME);
            String location = regionOf(clientIp);
            String device = SolvelaStringUtil.isBlank(deviceType) ? "未知设备" : deviceType;
            log.warn("【可疑登录】通知主人, memberId: {}, 地点: {}", memberId, location);
            sendExecutor.execute(() -> deliver(memberId, loginTime, device, location));
        } catch (RuntimeException e) {
            log.error("【可疑登录】通知主人失败, memberId: {}", memberId, e);
        }
    }

    /**
     * 抢一次发送额度。
     *
     * <p>🔴 用 {@code SET NX EX} 的语义（{@link RedisService#increment} 返回 1 即首次）——
     * 「读一下还没发过就发」在并发的几次尝试里会同时通过，而撞库恰恰是并发的。
     *
     * <p>Redis 挂了时<b>不发</b>（往少的方向偏）：节流判断不了的时候宁可漏一封，
     * 也不要在故障期间对着同一个人连发几十封。
     */
    private boolean acquire(Long memberId) {
        try {
            String key = redisService.generateRedisKey(KEY_THROTTLE, String.valueOf(memberId));
            return redisService.increment(key, throttle.toSeconds()) == 1L;
        } catch (RuntimeException e) {
            log.error("【可疑登录】节流判断失败，本次不发, memberId: {}", memberId, e);
            return false;
        }
    }

    private void deliver(Long memberId, String loginTime, String device, String location) {
        // 站内信：send() 自己吞异常。SYSTEM 分类，用户关不掉免打扰 ——
        // 「有人在试你的密码」不该是可以被静音的东西
        notificationService.send(NotifyRequest.of(NotificationTemplateEnum.SUSPICIOUS_LOGIN, memberId)
                .param("loginTime", loginTime)
                .param("deviceType", device)
                .param("location", location)
                .build());
        try {
            Member member = memberAuthDao.selectForEmailBind(memberId);
            if (member == null || SolvelaStringUtil.isEmpty(member.getEmail())) {
                // 没绑邮箱就只有站内信。而站内信要登进来才看得到 ——
                // 这正是「手机号注册开放之后要尽快接短信」的一个理由
                return;
            }
            mailService.sendMail(MailTemplateCodeEnum.MEMBER_SUSPICIOUS_LOGIN,
                    Map.of("loginTime", loginTime, "deviceType", device, "location", location),
                    List.of(piiCipher.decrypt(member.getEmail())));
        } catch (Exception e) {
            log.error("【可疑登录】提醒邮件发送失败, memberId: {}", memberId, e);
        }
    }

    /** IP 归属地；解析不出来时说「未知地点」，不把 IP 原样发给用户（那对他没意义） */
    private static String regionOf(String ip) {
        try {
            String region = SolvelaIpUtil.getRegion(ip);
            return SolvelaStringUtil.isBlank(region) ? "未知地点" : region;
        } catch (Exception e) {
            return "未知地点";
        }
    }
}
