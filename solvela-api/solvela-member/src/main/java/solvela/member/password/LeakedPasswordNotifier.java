package solvela.member.password;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import solvela.base.module.redis.RedisService;
import solvela.enums.NotificationTemplateEnum;
import solvela.notification.domain.NotifyRequest;
import solvela.notification.service.NotificationService;

import java.time.Duration;

/**
 * 存量账号的密码卫生提醒：<b>登录成功的那一刻</b>发现他正在用的密码已泄露，给一条站内信。
 *
 * <h3>🔴 为什么只能在这一刻判断</h3>
 * 库里存的是 Argon2 哈希，而哈希<b>不能</b>拿去跟一张明文表比对 ——
 * 想知道「这个账号的密码在不在泄露库里」，唯一的机会就是用户自己把明文送上来的那一次。
 * 所以这件事既不能做成定时任务，也不能在后台一键扫描。
 *
 * <p>{@code WeakPasswordChecker} 在注册与重置密码两处挡的是<b>新设的</b>密码；
 * 一个 2024 年就设了 {@code Password1} 的人，不会因为我们后来加了那张表而自动变安全。
 * 这个类就是补那一块。
 *
 * <h3>🔴 不拦登录</h3>
 * 拦了等于把人锁在自己账号外面 —— 而他并没有做错什么，是这个世界变了。
 * 能做的只有<b>告诉他</b>，然后让他自己去改。
 *
 * <h3>🔴 只发站内信，不发邮件</h3>
 * 「你的密码不安全」是钓鱼邮件最爱的模板，而这件事<b>没有任何紧急性</b>（没有人正在攻击他）。
 * 而且站内信恰好是最合适的渠道：他此刻<b>正在登录</b>，离「去改密码」只有两步。
 * 与此相对，{@code SuspiciousLoginNotifier} 发邮件，因为那一封说的是「有人正在试你的密码」。
 *
 * <h3>节流默认 30 天</h3>
 * 说一次是提醒，每次登录都说是骚扰 —— 而他学到的是忽略 SYSTEM 分类的所有消息。
 * 30 天也给了「我知道了，这周末再改」留出空间。
 *
 * @Date 2026-10-17
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LeakedPasswordNotifier {

    /** 节流键：一个会员这段时间内只提醒一次 */
    private static final String KEY_THROTTLE = "mbr:pwd:leaked-notify:";

    private final WeakPasswordChecker checker;

    private final NotificationService notificationService;

    private final RedisService redisService;

    /**
     * 总开关。
     *
     * <p>⚠️ 前缀是 {@code solvela.member.login.*}，与新设备提醒、可疑登录两个开关并排 ——
     * 理由见 {@code SuspiciousLoginNotifier}（{@code risk.*} 整个前缀归
     * {@code MemberRiskProperties} 管，在那底下用 {@code @Value} 塞键，读那个类的人看不到）。
     */
    @Value("${solvela.member.login.notify-leaked-password:true}")
    private boolean enabled;

    @Value("${solvela.member.login.notify-leaked-password-throttle:30d}")
    private Duration throttle;

    /**
     * 密码刚验对，顺手看一眼它在不在泄露表里。
     *
     * <h3>🔴 任何异常都只记日志</h3>
     * 它挂在登录的主干上，而这条提醒的价值<b>再高也高不过登录本身</b> ——
     * 与 {@code saveLoginLog}、风控落库同一条纪律。
     *
     * @param rawPassword 用户刚提交的明文。<b>只用来查表，不落库、不进日志</b>
     */
    public void onPasswordVerified(Long memberId, String rawPassword) {
        if (!enabled || memberId == null) {
            return;
        }
        try {
            if (!checker.leaked(rawPassword)) {
                return;
            }
            if (!acquire(memberId)) {
                // 节流中。这条日志用 debug：它会很常见（每次登录都会走到），而它不说明任何问题
                log.debug("【密码风险】提醒节流中，本次不发, memberId: {}", memberId);
                return;
            }
            // 🔴 WARN 而不是 INFO：「有多少活跃账号在用泄露密码」是一个值得被捞出来看的数，
            //    而它目前没有别的出口（看板统计的是风控流水，这件事不进那张表）
            log.warn("【密码风险】该会员正在使用已泄露的密码，已提醒, memberId: {}", memberId);
            notificationService.send(
                    NotifyRequest.of(NotificationTemplateEnum.LEAKED_PASSWORD, memberId).build());
        } catch (RuntimeException e) {
            log.error("【密码风险】提醒失败, memberId: {}", memberId, e);
        }
    }

    /**
     * 抢一次提醒额度。
     *
     * <p>用 {@code INCR + EXPIRE} 的「返回 1 即首次」语义（同 {@code SuspiciousLoginNotifier}）：
     * 「读一下还没发过就发」在并发登录里会同时通过。
     *
     * <p>Redis 挂了时<b>不发</b>：往少的方向偏 —— 宁可漏一条提醒，
     * 也不要在故障期间对着同一个人每次登录都发一条。
     */
    private boolean acquire(Long memberId) {
        try {
            String key = redisService.generateRedisKey(KEY_THROTTLE, String.valueOf(memberId));
            return redisService.increment(key, throttle.toSeconds()) == 1L;
        } catch (RuntimeException e) {
            log.error("【密码风险】节流判断失败，本次不发, memberId: {}", memberId, e);
            return false;
        }
    }
}
