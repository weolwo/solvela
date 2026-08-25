package solvela.draw.runtime;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import solvela.draw.poolitem.domain.entity.PrizePoolItem;
import org.redisson.api.RScript;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 抽奖库存 Redis 服务：预热 + Lua 原子预扣（防超发第一道防线）
 * 最终一致性由 DB 条件更新（防超发第二道防线）兜底
 *
 * @Author alaric
 * @Date 2026-07-26
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class DrawStockService {

    private final RedissonClient redissonClient;

    private static final int UNLIMITED = -1;

    /**
     * 库存预扣 + 单人限领校验，单脚本原子执行
     * KEYS[1]=库存key KEYS[2]=单人已领key(含周期段) ARGV[1]=单人限领上限(-1不限) ARGV[2]=计数key的TTL秒(0不过期)
     * 返回：1成功 / -1库存不足 / -2超单人限领 / -3缓存未预热
     *
     * ⚠️ EXPIRE 必须在 INCR <b>之后</b>且在同一脚本内：分成两次调用的话，
     * INCR 完成而 EXPIRE 尚未执行时进程崩溃，会留下一个<b>永不过期</b>的计数 key，
     * 那个用户在该奖项上就被永久锁死了。
     *
     * 每次 INCR 都重设 TTL 是刻意的：周期由 key 名决定，TTL 只负责回收，
     * 重设不会让窗口变长（换周期就是换 key），却能保证「周期内最后一次抽奖之后
     * 仍留足回收余量」，避免活跃用户的计数被提前清掉。
     */
    private static final String DEDUCT_LUA = """
            local stock = redis.call('GET', KEYS[1])
            if stock == false then return -3 end
            stock = tonumber(stock)
            if stock == 0 then return -1 end
            local userMax = tonumber(ARGV[1])
            if userMax ~= -1 then
                local got = tonumber(redis.call('GET', KEYS[2]) or '0')
                if got >= userMax then return -2 end
            end
            if stock > 0 then redis.call('DECR', KEYS[1]) end
            redis.call('INCR', KEYS[2])
            local ttl = tonumber(ARGV[2])
            if ttl > 0 then redis.call('EXPIRE', KEYS[2], ttl) end
            return 1
            """;

    /**
     * 预扣回滚（DB 兜底拒绝后补偿）：库存加回（不限量-1不动）、单人已领减回
     */
    private static final String ROLLBACK_LUA = """
            local stock = tonumber(redis.call('GET', KEYS[1]) or '-999')
            if stock ~= -999 and stock >= 0 then redis.call('INCR', KEYS[1]) end
            local got = tonumber(redis.call('GET', KEYS[2]) or '0')
            if got > 0 then redis.call('DECR', KEYS[2]) end
            return 1
            """;

    /**
     * 发布/保存后预热库存缓存：剩余库存 = 总量 - 已发；-1 表示不限量
     */
    public void warmStock(String activityCode, List<PrizePoolItem> items) {
        for (PrizePoolItem item : items) {
            int remain = UNLIMITED == item.getTotalStock()
                    ? UNLIMITED
                    : Math.max(0, item.getTotalStock() - (item.getUsedStock() == null ? 0 : item.getUsedStock()));
            redissonClient.getBucket(DrawCacheKey.stock(activityCode, item.getId()), StringCodec.INSTANCE)
                    .set(String.valueOf(remain));
        }
        log.info("[抽奖库存预热] activityCode={}, 奖项数={}", activityCode, items.size());
    }

    /**
     * 读取剩余库存快照；key 缺失返回 null（由调用方回源预热）
     */
    public Integer getRemainStock(String activityCode, long prizeItemId) {
        Object value = redissonClient.getBucket(DrawCacheKey.stock(activityCode, prizeItemId), StringCodec.INSTANCE).get();
        return value == null ? null : Integer.parseInt(value.toString());
    }

    /**
     * 运行期回源预热：只在 key 不存在时写入（SET NX），并返回实际生效的剩余库存
     *
     * 与 {@link #warmStock} 的无条件 SET 区别很关键：缓存冷启动 + 高并发时，
     * 若多个线程都读到 null 而各自无条件 SET，后一次 SET 会把中间发生的扣减覆盖掉，直接导致超发。
     * 配置态保存要的是「以 DB 为准强制刷新」，运行态回源要的是「补空洞但绝不覆盖」，语义必须分开。
     */
    public int warmStockIfAbsent(String activityCode, PrizePoolItem item) {
        int remain = UNLIMITED == item.getTotalStock()
                ? UNLIMITED
                : Math.max(0, item.getTotalStock() - (item.getUsedStock() == null ? 0 : item.getUsedStock()));
        var bucket = redissonClient.getBucket(DrawCacheKey.stock(activityCode, item.getId()), StringCodec.INSTANCE);
        bucket.setIfAbsent(String.valueOf(remain));
        // 抢输的线程要以别人写入（或已被扣减）的值为准，不能返回自己算的那个
        Object current = bucket.get();
        return current == null ? remain : Integer.parseInt(current.toString());
    }

    /**
     * 预扣。
     *
     * @param period 当前周期桶，由 {@link DrawPeriodResolver} 按奖池 reset_period 算出。
     *               ⚠️ 同一次请求的 deduct 与 rollback <b>必须传同一个 period</b>，
     *               否则回滚会去减另一个桶的计数：本桶的计数永远退不回来，
     *               用户白白损失一次限领额度。
     */
    public StockDeductResult deduct(String activityCode, long prizeItemId, long memberId,
                                    int userMaxCount, DrawPeriodResolver.Period period) {
        Long code = redissonClient.getScript(StringCodec.INSTANCE).eval(
                RScript.Mode.READ_WRITE,
                DEDUCT_LUA,
                RScript.ReturnType.LONG,
                List.of(DrawCacheKey.stock(activityCode, prizeItemId),
                        DrawCacheKey.userCount(activityCode, prizeItemId, period.bucket(), memberId)),
                String.valueOf(userMaxCount), String.valueOf(period.ttlSeconds()));
        return StockDeductResult.ofLuaCode(code);
    }

    public void rollback(String activityCode, long prizeItemId, long memberId,
                         DrawPeriodResolver.Period period) {
        redissonClient.getScript(StringCodec.INSTANCE).eval(
                RScript.Mode.READ_WRITE,
                ROLLBACK_LUA,
                RScript.ReturnType.LONG,
                List.of(DrawCacheKey.stock(activityCode, prizeItemId),
                        DrawCacheKey.userCount(activityCode, prizeItemId, period.bucket(), memberId)));
    }
}
