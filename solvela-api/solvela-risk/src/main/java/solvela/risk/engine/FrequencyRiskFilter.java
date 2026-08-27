package solvela.risk.engine;

import solvela.base.module.redis.RedisService;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import solvela.risk.PromotionConfig;
import org.springframework.stereotype.Service;

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
        long currentCount = redisService.increment(redisKey, calculateExpireSeconds(config.getLimitPeriod()));

        if (currentCount > config.getIdentifyLimit()) {
            return RiskResult.reject(RiskBlockCode.USER_FREQUENCY_LIMIT, "您的参与太频繁了，请稍后再试");
        }

        return RiskResult.pass();
    }

    private long calculateExpireSeconds(String period) {
        return switch (period) {
            case "DAILY" -> 86400L;
            case "WEEKLY" -> 86400L * 7;
            default -> 86400L; // 默认给个一天
        };
    }
}
