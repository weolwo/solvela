package solvela.member.risk.feature;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import solvela.base.module.redis.RedisService;
import solvela.base.util.SolvelaStringUtil;
import solvela.enums.MemberRiskModeEnum;
import solvela.member.risk.RiskContext;
import solvela.member.risk.config.RiskRuleRegistry;
import solvela.member.risk.rule.RiskRuleConfig;
import solvela.member.risk.rule.impl.PasswordCooldownRule;

import java.time.Duration;
import java.util.List;

/**
 * 密码连续错误的阶梯冷却：计数、算这一档冷却多久、自助解除。
 *
 * <h3>🔴 按身份摘要计数，不按会员号</h3>
 * 按会员号的话，<b>不存在的账号永远不会被冷却</b> —— 攻击者故意多错几次，看会不会出现冷却提示，
 * 就知道这个号存不存在。摘要在查无此人时照样算得出来，于是两条路径的行为逐字相同。
 *
 * <h3>🔴 分桶：别让任何人能把别人锁在门外</h3>
 * 只按身份计数的话，<b>任何知道你邮箱的人连错几次就能让你进不来</b> —— 限流本身成了攻击手段。
 * 所以计数分两个桶：
 * <ul>
 *   <li><b>可信设备</b>（{@code 设备号}）：主人自己那台，各算各的；</li>
 *   <li><b>其余一律共用</b>（{@code u}）：所有陌生设备、没带令牌的请求挤在一个桶里。
 *       攻击者换设备令牌也躲不开它。</li>
 * </ul>
 * 于是攻击者在陌生设备上错满，冷却的是 {@code u} 桶，而<b>主人在自己的老设备上照常能用密码登录</b>。
 * 这正是「账号信息只能用来放宽」的一个应用：可信桶只对存在的账号存在，攻击者永远落在 {@code u} 桶。
 *
 * <p>算不出信任时退回 {@code u}（往严的方向偏）。代价是那一刻主人可能和攻击者共用一个桶 ——
 * 而那也只锁密码这一条通道，验证码登录和找回密码都还在。
 *
 * <h3>只锁密码这一个通道</h3>
 * 本类只被密码登录的失败路径调用。邮箱验证码登录、找回密码都不受影响，
 * 所以被恶意冷却的人随时有路可走 —— 这是整套设计敢用 24 小时那一档的前提。
 *
 * @Date 2026-10-15
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PasswordCooldownStore {

    private static final String KEY_PREFIX = "mbr:risk:pwd:";

    /** 所有不可信设备共用的桶 */
    private static final String SHARED_BUCKET = "u";

    private final RedisService redisService;

    /** 续期要 EXPIRE，而 {@link RedisService} 被刻意裁剪过（没有这个方法）。同 {@code MemberRedisTokenStore} 的做法 */
    private final StringRedisTemplate redis;

    /** 延迟取：规则 bean 的构造会先于本类完成，构造器直接注入 registry 就是循环依赖（同 {@link LoginCounters}） */
    private final ObjectProvider<RiskRuleRegistry> registry;

    /**
     * 这一次密码错误之后的状态。
     *
     * @param failures  含这一次的连续失败次数
     * @param cooldown  这一次要冷却多久；还在免罚次数内时为 {@link Duration#ZERO}
     */
    public record Outcome(long failures, Duration cooldown) {

        public boolean cooling() {
            return !cooldown.isZero();
        }
    }

    /**
     * 记一次密码错误并返回该冷却多久。
     *
     * @param free      前几次不冷却（手滑不该一次就等一分钟）
     * @param steps     阶梯，从第 {@code free + 1} 次失败起依次取用；用完之后一直停在最后一档
     * @param idleReset 多久没有新的失败就清零（也是计数键的 TTL）
     */
    public Outcome recordFailure(String identityHashHex, String bucket, int free, List<Duration> steps,
                                 Duration idleReset) {
        if (SolvelaStringUtil.isBlank(identityHashHex) || steps.isEmpty()) {
            return new Outcome(0L, Duration.ZERO);
        }
        /*
         * 每次失败都续期：窗口的语义是「<b>连续</b>失败」—— 隔了很久的一次失败不该算在同一串里，
         * 而两次只隔几分钟的就该算。这与计数类限流的固定窗口刻意不同。
         *
         * 🔴 续期用 EXPIRE，不是「读出来再 set 回去」：后者在两个请求同时失败时会用自己读到的旧值
         * 覆盖掉对方刚写的新值 —— 丢一次计数，而丢掉的恰好是并发撞库时的计数。
         * INCR 自己是原子的，EXPIRE 是幂等的，两条命令之间出任何问题都不会让计数变少。
         */
        String countKey = key(identityHashHex, bucket);
        long failures = redisService.increment(countKey, idleReset.toSeconds());
        redis.expire(countKey, idleReset);
        if (failures <= free) {
            return new Outcome(failures, Duration.ZERO);
        }
        int index = (int) Math.min(failures - free, steps.size()) - 1;
        Duration cooldown = steps.get(index);
        setCooldown(identityHashHex, bucket, cooldown);
        return new Outcome(failures, cooldown);
    }

    /** 还在冷却中就返回剩余秒数，否则 0 */
    public long remainingSeconds(String identityHashHex, String bucket) {
        if (SolvelaStringUtil.isBlank(identityHashHex)) {
            return 0L;
        }
        long ttl = redisService.getExpire(cooldownKey(identityHashHex, bucket));
        return Math.max(0L, ttl);
    }

    public long failures(String identityHashHex, String bucket) {
        String raw = redisService.get(key(identityHashHex, bucket));
        if (raw == null) {
            return 0L;
        }
        try {
            return Long.parseLong(raw);
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    /**
     * 清零：登录成功、重置密码成功时调用。
     *
     * <p>🔴 <b>把两个桶都清掉</b>。只清当前这个桶的话，主人在老设备上登录成功之后，
     * 攻击者在 {@code u} 桶里攒的计数还在 —— 而主人下次换台新手机登录就会一脚踩进去。
     */
    public void clear(String identityHashHex, String deviceId) {
        if (SolvelaStringUtil.isBlank(identityHashHex)) {
            return;
        }
        redisService.delete(key(identityHashHex, SHARED_BUCKET), cooldownKey(identityHashHex, SHARED_BUCKET));
        if (!SolvelaStringUtil.isBlank(deviceId)) {
            redisService.delete(key(identityHashHex, deviceId), cooldownKey(identityHashHex, deviceId));
        }
    }

    /** 这次请求该落在哪个桶：可信设备各算各的，其余一律共用 */
    public String bucket(String deviceId, boolean deviceTrusted) {
        return deviceTrusted && !SolvelaStringUtil.isBlank(deviceId) ? deviceId : SHARED_BUCKET;
    }

    /**
     * 认证主干用的那一个入口：密码验错了，记一次。
     *
     * <p>参数（免罚次数、阶梯、清零时间）从规则表读 —— 与 {@code PasswordCooldownRule} 看同一份配置，
     * 否则「判的时候按新参数、记的时候按旧参数」。规则关闭或读不出来时什么都不做。
     */
    public void onPasswordFailure(RiskContext ctx) {
        if (ctx.getIdentityHashHex() == null) {
            return;
        }
        RiskRuleConfig config = config();
        if (config == null || config.mode() == MemberRiskModeEnum.OFF) {
            return;
        }
        try {
            Outcome outcome = recordFailure(ctx.getIdentityHashHex(),
                    bucket(ctx.getDeviceId(), ctx.deviceTrusted()),
                    PasswordCooldownRule.free(config.params()),
                    PasswordCooldownRule.steps(config.params()),
                    PasswordCooldownRule.idleReset(config.params()));
            if (outcome.cooling()) {
                log.info("【密码冷却】连续错误第 {} 次，冷却 {}", outcome.failures(), outcome.cooldown());
            }
        } catch (RuntimeException e) {
            // 记不上只是「这次没算进去」，不该让登录接口跟着失败
            log.error("【密码冷却】记一次失败时出错", e);
        }
    }

    /**
     * 登录成功、重置密码成功时清零。
     *
     * <p>🔴 重置密码那条路是<b>被恶意冷却时的唯一自助出口</b>（方案 §4.4）：
     * 不在那里清零的话，用户改完密码还是进不来，而他已经用完了所有能自己做的事。
     */
    public void onIdentityVerified(String identityHashHex, String deviceId) {
        try {
            clear(identityHashHex, deviceId);
        } catch (RuntimeException e) {
            log.error("【密码冷却】清零失败", e);
        }
    }

    private RiskRuleConfig config() {
        try {
            return registry.getObject().config("PWD_COOLDOWN");
        } catch (RuntimeException e) {
            log.warn("【密码冷却】读规则配置失败，本次不记", e);
            return null;
        }
    }

    public String sharedBucket() {
        return SHARED_BUCKET;
    }

    private void setCooldown(String identityHashHex, String bucket, Duration cooldown) {
        redisService.set(cooldownKey(identityHashHex, bucket), "1", cooldown.toSeconds());
    }

    private String key(String identityHashHex, String bucket) {
        return redisService.generateRedisKey(KEY_PREFIX, identityHashHex + ":" + bucket);
    }

    private String cooldownKey(String identityHashHex, String bucket) {
        return redisService.generateRedisKey(KEY_PREFIX, identityHashHex + ":" + bucket + ":cd");
    }
}
