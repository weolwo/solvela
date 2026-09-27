package solvela.member.auth;

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
import solvela.enums.LoginLogResultEnum;
import solvela.enums.NotificationTemplateEnum;
import solvela.member.Member;
import solvela.member.email.EmailSendExecutorConfig;
import solvela.member.loginlog.dao.MemberLoginLogDao;
import solvela.notification.domain.NotifyRequest;
import solvela.notification.service.NotificationService;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * 新设备登录提醒：账号在一台<b>从没登录成功过</b>的设备上登录成功时，给主人发一封邮件 + 一条站内信。
 *
 * <h3>为什么要它</h3>
 * 「我的登录设备」能看到异常登录，但前提是主人自己想起来去翻。盗号者登进来的那一刻就通知他，
 * 他才有机会在东西被搬走之前改密码、下线其他设备。
 *
 * <h3>什么时候发</h3>
 * <ul>
 *   <li>这台设备上该会员<b>从没</b>成功登录过；</li>
 *   <li>并且这台设备<b>不是注册时用的那台</b>（{@link #rememberRegistrationDevice}）
 *       —— 在注册的那台上重新登录不是「新设备」，给新用户发「异地登录」告警只会教会他们忽略这类信；</li>
 *   <li>请求有设备号 —— 没有设备身份就判断不了「新不新」，宁可不发也不乱发。</li>
 * </ul>
 *
 * <h3>🔴 为什么要单独记住注册设备</h3>
 * 注册成功直接就是登录态，但刻意不写登录日志（见 MemberRegisterService#createMember）。
 * 2026-09-27 初版的判据是「该会员以前成功登录过」，结果对<b>绝大多数用户</b>整个失效：
 * 注册完一直用着注册那次的会话，从没「登录」过 —— 盗号者在别处的那一次登录，
 * 恰好是这个账号的「第一次登录」，被当成新用户首登跳过了。上线后实测才发现。
 * 现在注册时把设备号记进 Redis，判据改成「是不是注册那台」。
 *
 * <p>记录不存在（这条记录上线前注册的老会员、或 Redis 丢了）时退回初版判据 ——
 * 分不清的时候不发，和「没有设备号就不发」同一个取向。
 * 判断必须在写本次成功日志<b>之前</b>做（调用方保证）。
 *
 * <h3>发送是异步的</h3>
 * 两个存在性查询在登录线程里做（主键 / 索引点查），发信与站内信丢到发信线程池 ——
 * 它们慢或失败都不该拖住、更不该搞砸一次成功的登录。
 *
 * <h3>为什么邮件、站内信都发</h3>
 * 站内信要登进来才看得到，而盗号者登进来之后能先把它标成已读；邮件在主人自己的邮箱里。
 *
 * @Date 2026-09-27
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NewDeviceLoginNotifier {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final MemberLoginLogDao loginLogDao;

    private final MemberAuthDao memberAuthDao;

    private final PiiCipher piiCipher;

    private final MailService mailService;

    private final NotificationService notificationService;

    private final RedisService redisService;

    /** 注册设备：会员 → 注册时的设备号 */
    private static final String KEY_REGISTRATION_DEVICE = "mbr:login:reg-device:";

    /** 与设备 cookie 同寿（400 天）：cookie 还在，就还能认出「这是注册那台」 */
    private static final long REGISTRATION_DEVICE_TTL_SECONDS = 400L * 24 * 3600;

    /** 发信线程池。容器里有多个 AsyncTaskExecutor，按名字精确注入，理由同 MemberEmailCodeService */
    @jakarta.annotation.Resource(name = EmailSendExecutorConfig.EMAIL_SEND_EXECUTOR)
    private AsyncTaskExecutor sendExecutor;

    /** 总开关：误发成灾时的回退，不用发版 */
    @Value("${solvela.member.login.notify-new-device:true}")
    private boolean enabled;

    /**
     * 注册成功时调用：记住这个会员是在哪台设备上注册的。任何异常都只记日志，绝不影响注册。
     */
    public void rememberRegistrationDevice(Long memberId, String deviceId) {
        if (memberId == null || SolvelaStringUtil.isBlank(deviceId)) {
            return;
        }
        try {
            redisService.set(registrationDeviceKey(memberId), deviceId, REGISTRATION_DEVICE_TTL_SECONDS);
        } catch (Exception e) {
            log.warn("【新设备登录】记录注册设备失败，该会员之后按老判据处理, memberId: {}", memberId, e);
        }
    }

    /**
     * 登录成功时调用（写本次成功日志之前）。任何异常都只记日志，绝不影响登录。
     */
    public void onLoginSucceeded(Long memberId, String deviceId, String deviceType, String clientIp) {
        if (!enabled || memberId == null || SolvelaStringUtil.isBlank(deviceId)) {
            return;
        }
        try {
            int success = LoginLogResultEnum.LOGIN_SUCCESS.getValue();
            String registrationDevice = redisService.get(registrationDeviceKey(memberId));
            if (registrationDevice == null) {
                // 不知道注册设备：退回初版判据，从没登录过就当成注册后首登
                if (!loginLogDao.existsSuccessfulLogin(memberId, success)) {
                    return;
                }
            } else if (registrationDevice.equals(deviceId)) {
                return;
            }
            if (loginLogDao.existsSuccessfulLoginOnDevice(memberId, deviceId, success)) {
                return;
            }
            String loginTime = LocalDateTime.now().format(TIME);
            String location = regionOf(clientIp);
            String device = SolvelaStringUtil.isBlank(deviceType) ? "未知设备" : deviceType;
            log.info("【新设备登录】发送提醒, memberId: {}, deviceId: {}, 地点: {}", memberId, deviceId, location);
            sendExecutor.execute(() -> deliver(memberId, loginTime, device, location, deviceId));
        } catch (Exception e) {
            log.error("【新设备登录】判断失败，本次不提醒, memberId: {}", memberId, e);
        }
    }

    private void deliver(Long memberId, String loginTime, String device, String location, String deviceId) {
        Map<String, Object> params = Map.of("loginTime", loginTime, "deviceType", device, "location", location);
        // 站内信：send() 自己吞异常
        notificationService.send(NotifyRequest.of(NotificationTemplateEnum.NEW_DEVICE_LOGIN, memberId)
                .param("loginTime", loginTime)
                .param("deviceType", device)
                .param("location", location)
                .bizRefId(deviceId)
                .build());
        // 邮件：没绑邮箱就只有站内信
        try {
            Member member = memberAuthDao.selectForEmailBind(memberId);
            if (member == null || SolvelaStringUtil.isEmpty(member.getEmail())) {
                return;
            }
            mailService.sendMail(MailTemplateCodeEnum.MEMBER_NEW_DEVICE_LOGIN, params,
                    List.of(piiCipher.decrypt(member.getEmail())));
        } catch (Exception e) {
            log.error("【新设备登录】提醒邮件发送失败, memberId: {}", memberId, e);
        }
    }

    private String registrationDeviceKey(Long memberId) {
        return redisService.generateRedisKey(KEY_REGISTRATION_DEVICE, String.valueOf(memberId));
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
