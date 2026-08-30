package solvela.risk.engine;

import solvela.base.module.redis.RedisService;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import solvela.risk.PromotionConfig;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

/**
 * 第二梯队：高频防刷规则（Order = 20）
 * 特点：拦截绝大部分的黑产/羊毛党。必须依赖 Redis 处理，防止数据库被打挂。对应你表里的
 */
@Slf4j
@Service
public class FrequencyRiskFilter implements IRiskFilter {
    @Resource
    private RedisService redisService;

    @Override
    public int getOrder() {
        return 20; // 第二优先级
    }

    @Override
    public RiskResult doFilter(RiskContext context) {
        PromotionConfig config = context.getConfig();
        // 🔴 频次 key 必须用 member_id：账号可改，改名之后同一个人换一个 key 重新计数，
        //    「单人限领」当场归零 —— 这正是薅羊毛要的效果，而且计数看上去完全正常
        Long uid = context.getRequest().getMemberId();
        Long promoId = config.getId();

        // 如果未配置单人频次限制，直接放行
        if (config.getIdentifyLimit() == null || config.getIdentifyLimit() <= 0) {
            return RiskResult.pass();
        }

        // ==========================================
        // 核心：基于 Redis 的滑动/固定窗口限流
        // ==========================================
        // 缓存 Key 示例: risk:freq:DAILY:promo_1001:5579345309
        String redisKey = String.format("risk:freq:%s:promo_%d:%s", config.getLimitPeriod(), promoId, uid);

        // INCR 与首次 EXPIRE 必须原子完成 —— 分成两条命令时，两者之间一旦中断
        // 就会留下一个没有 TTL 的计数键，这个用户对这个活动的限流<b>再也不会解除</b>。
        // 原因见 RedisService.increment 的注释。
        long currentCount = redisService.increment(redisKey, calculateExpireSeconds(config));

        if (currentCount > config.getIdentifyLimit()) {
            return RiskResult.reject(RiskBlockCode.USER_FREQUENCY_LIMIT, "您的参与太频繁了，请稍后再试");
        }

        return RiskResult.pass();
    }

    private static final long ONE_DAY = 86400L;

    /**
     * 「终身」的 TTL。
     *
     * <p>不能真的不设过期时间：{@code RedisService.increment} 的 Lua 脚本发现 PTTL == -1
     * 会重新补一次 EXPIRE，无 TTL 的键在这里表达不出来；而且没有 TTL 的计数键
     * 会在 Redis 里无限堆积，一场大促下来就是几百万个永不回收的 key。
     * 十年对一个营销活动而言就是终身。
     */
    private static final long LIFETIME_SECONDS = ONE_DAY * 365 * 10;

    /**
     * 把限制周期换算成计数键的 TTL。
     *
     * <p>🔴 这里原先只认 DAILY / WEEKLY，<b>LIFETIME 和 MONTHLY 都掉进 default 变成了一天</b>：
     * 配「终身限领 1 次」的活动，用户第二天就能再领一次，而且计数看上去完全正常、
     * 没有任何报错 —— 与「策略工厂拿 String 查枚举键」是同一类静默失效。
     *
     * <p>注意这是<b>固定窗口</b>而不是自然周期：TTL 只在计数键首次创建时设置
     * （见 {@code RedisService.increment} 的 Lua），所以 DAILY 的真实语义是
     * 「首次参与后的 24 小时内」，不是「自然日内」。要改成自然日得换成
     * {@code RedisService.currentDaySecond()}，那会改变所有存量活动的行为，故此处不动。
     */
    private long calculateExpireSeconds(PromotionConfig config) {
        String period = config.getLimitPeriod();
        if (period == null) {
            return ONE_DAY;
        }
        return switch (period) {
            case "DAILY" -> ONE_DAY;
            case "WEEKLY" -> ONE_DAY * 7;
            case "MONTHLY" -> ONE_DAY * 31;
            case "LIFETIME" -> LIFETIME_SECONDS;
            case "CUSTOM" -> customWindowSeconds(config);
            // 库里出现了字典外的取值：宁可按最短的一天算，也不能放行
            default -> {
                log.warn("【风控配置异常】未知的限制周期 [{}]，按一天处理。配置ID: {}", period, config.getId());
                yield ONE_DAY;
            }
        };
    }

    /**
     * 自定义窗口：计数键活到 {@code limit_end_time} 为止，窗口一过计数自然清零。
     *
     * <p>窗口已经结束（或压根没配结束时间）时<b>回退成一天而不是放行</b>：
     * EXPIRE 传非正数会让 Redis 直接删键，于是每次请求都是「计数 1」，限领当场归零 ——
     * 那正是薅羊毛要的效果。窗口过期还在发奖本身就是配置问题，此时宁可多限也不能不限。
     */
    private long customWindowSeconds(PromotionConfig config) {
        LocalDateTime end = config.getLimitEndTime();
        if (end == null) {
            log.warn("【风控配置异常】限制周期为 CUSTOM 但没有结束时间，按一天处理。配置ID: {}", config.getId());
            return ONE_DAY;
        }
        long seconds = Duration.between(LocalDateTime.now(), end).getSeconds();
        if (seconds <= 0) {
            log.warn("【风控配置异常】限制周期窗口已于 {} 结束，仍在发奖，按一天处理。配置ID: {}", end, config.getId());
            return ONE_DAY;
        }
        return seconds;
    }
}
