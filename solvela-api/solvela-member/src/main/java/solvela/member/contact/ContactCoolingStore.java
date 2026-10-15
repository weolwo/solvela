package solvela.member.contact;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import solvela.base.module.redis.RedisService;
import solvela.enums.MemberVerifyChannelEnum;

import java.time.Duration;

/**
 * 新绑联系方式的<b>冷静期</b>：在不受信任的设备上绑的邮箱 / 手机号，7 天内不能用来核身。
 *
 * <h3>🔴 它堵的是一条完整的账号接管链</h3>
 * 首次绑定邮箱<b>不要求任何额外验证</b>（{@code MemberEmailBindService} 的类注释写着
 * 「首次绑定不要求这一步」），绑定接口也没挂 {@code @StepUpRequired}。
 * 于是对一个没有邮箱的账号（手机号注册），一个<b>知道密码</b>的人在新设备上可以：
 *
 * <pre>
 *   登录 → 加地址被要求二次验证 → 没有邮箱 → 绑自己的邮箱 → 用它通过二次验证 → 把东西搬走
 *   或者更糟：用这个邮箱走找回密码 → 永久接管
 * </pre>
 *
 * <p>而方案 §6.4 要在「没绑就去绑定」这件事上给用户一个<b>显眼的按钮</b> ——
 * 那等于把这条链铺成主路。所以按钮要给，冷静期必须一起上。
 *
 * <h3>为什么按「绑定时那台设备可信吗」分，而不是一律冷静 7 天</h3>
 * 主人在自己的老手机上补绑邮箱，是最正常的事，拦他 7 天纯属添乱。
 * 而攻击者手里的设备恰恰是不受信任的那台 —— 这个判据正好把两者分开。
 *
 * <h3>三处都要挡</h3>
 * 二次验证发码、登录 OTP 的通道路由、找回密码。漏一处这条链就还通着 ——
 * 尤其是找回密码，它是三者里后果最重的那一个。
 *
 * <h3>为什么是 Redis 而不是一列</h3>
 * 它是一个<b>到点自动消失</b>的状态，7 天后不留任何痕迹。落在
 * {@code t_member} 上就要再加一列加一个清理任务，而「什么时候绑的」
 * 已经有 {@code t_data_tracer} 记着了。
 *
 * @Date 2026-10-16
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ContactCoolingStore {

    private static final String KEY = "mbr:contact:cool:";

    /** 7 天。够一个真被盗的人收到邮件、反应过来、改回密码 */
    static final Duration COOLING = Duration.ofDays(7);

    private final RedisService redisService;

    /**
     * 开始冷静期。<b>绑定成功、且绑定时那台设备不受信任</b>时调用。
     *
     * <p>重复调用会把到期时间往后推 —— 这是对的：又换了一次绑定，就从这一次重新算。
     */
    public void start(Long memberId, MemberVerifyChannelEnum channel) {
        if (memberId == null || channel == null) {
            return;
        }
        try {
            long availableAt = System.currentTimeMillis() / 1000L + COOLING.toSeconds();
            redisService.set(key(memberId, channel), String.valueOf(availableAt), COOLING.toSeconds());
            log.info("【联系方式冷静期】开始, memberId: {}, 通道: {}, 到期: {}", memberId, channel, availableAt);
        } catch (RuntimeException e) {
            /*
             * 🔴 记不上就是「这次没有冷静期」—— 往宽的方向偏。
             * 这与大多数风控判断相反，是刻意的：往严的方向偏意味着 Redis 一抖动，
             * 所有人的邮箱都突然不能用来核身、不能找回密码，而那是一个比漏过更大的故障。
             */
            log.error("【联系方式冷静期】记录失败，本次不设冷静期, memberId: {}", memberId, e);
        }
    }

    /**
     * 这条通道还要冷静到什么时候（Unix 秒）。<b>0 表示不在冷静期</b>。
     */
    public long availableAt(Long memberId, MemberVerifyChannelEnum channel) {
        if (memberId == null || channel == null) {
            return 0L;
        }
        try {
            String raw = redisService.get(key(memberId, channel));
            if (raw == null) {
                return 0L;
            }
            long at = Long.parseLong(raw);
            return at > System.currentTimeMillis() / 1000L ? at : 0L;
        } catch (RuntimeException e) {
            // 读不出来当「不在冷静期」，理由同 start
            log.error("【联系方式冷静期】读取失败，按不冷静处理, memberId: {}", memberId, e);
            return 0L;
        }
    }

    public boolean cooling(Long memberId, MemberVerifyChannelEnum channel) {
        return availableAt(memberId, channel) > 0L;
    }

    /**
     * 解除。<b>在受信任的设备上重新绑定</b>时调用 —— 主人回到自己的老手机上再绑一次，
     * 没有理由继续拦他。
     */
    public void clear(Long memberId, MemberVerifyChannelEnum channel) {
        if (memberId == null || channel == null) {
            return;
        }
        try {
            redisService.delete(key(memberId, channel));
        } catch (RuntimeException e) {
            log.error("【联系方式冷静期】解除失败, memberId: {}", memberId, e);
        }
    }

    private String key(Long memberId, MemberVerifyChannelEnum channel) {
        return redisService.generateRedisKey(KEY, memberId + ":" + channel.name());
    }
}
