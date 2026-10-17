package solvela.app.ratelimit;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import solvela.security.IpKeys;
import solvela.security.SecurityKeys;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.LongAdder;

/**
 * 封禁期间被安全闸挡掉的请求数（网关方案 §5.5「写入时机」）。回答「这个 IP 被我们挡掉了多少请求」这笔成本账。
 *
 * <h3>进程内先攒，每 {@link #FLUSH_INTERVAL} 秒一次 {@code INCRBY}</h3>
 * 被封的 IP 每秒打几千次，安全闸挡它们只花一次进程内缓存查找 —— 计数要是每次都去一次 Redis，
 * 挡请求这件事就又变贵了。代价是实例被杀时丢最后几秒的数，这是一个统计数，可以接受。
 *
 * <p>Redis 里的计数由后台任务同步到 {@code t_security_list.blocked_count}（{@code SecurityListMaintainJob}）。
 *
 * @Date 2026-10-17
 */
@Slf4j
@Component
public class BlockedCounter {

    static final long FLUSH_INTERVAL = 10;

    /** 计数键的 TTL：同步任务停了几天，计数也不会永远留在 Redis 里 */
    static final Duration KEY_TTL = Duration.ofDays(2);

    /** 一个周期里最多攒这么多个不同的值：被大量不同 IP 打的时候，多出来的那些这一轮不计 */
    static final int MAX_KEYS = 10_000;

    private final StringRedisTemplate redis;

    private final ConcurrentHashMap<String, LongAdder> pending = new ConcurrentHashMap<>();

    private ScheduledExecutorService executor;

    public BlockedCounter(StringRedisTemplate redis) {
        this.redis = redis;
    }

    /** 记一次「被挡下」。{@code listValue} 是 {@link IpKeys#listValue} 的写法 */
    void record(String listValue) {
        if (listValue == null) {
            return;
        }
        String key = SecurityKeys.blockedCounter(IpKeys.listDimension(listValue), listValue);
        LongAdder adder = pending.get(key);
        if (adder == null) {
            if (pending.size() >= MAX_KEYS) {
                return;
            }
            adder = pending.computeIfAbsent(key, k -> new LongAdder());
        }
        adder.increment();
    }

    @PostConstruct
    void start() {
        executor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "gw-blocked-counter");
            t.setDaemon(true);
            return t;
        });
        executor.scheduleWithFixedDelay(this::flush, FLUSH_INTERVAL, FLUSH_INTERVAL, TimeUnit.SECONDS);
    }

    @PreDestroy
    void stop() {
        if (executor != null) {
            executor.shutdownNow();
        }
        flush();
    }

    /** 把攒下的数写进 Redis。任何异常只打日志：{@code scheduleWithFixedDelay} 遇到未捕获的异常会静默地停掉后续所有执行 */
    void flush() {
        for (Map.Entry<String, LongAdder> e : pending.entrySet()) {
            long n = e.getValue().sumThenReset();
            if (n == 0) {
                pending.remove(e.getKey(), e.getValue());
                continue;
            }
            try {
                redis.opsForValue().increment(e.getKey(), n);
                redis.expire(e.getKey(), KEY_TTL);
            } catch (Exception ex) {
                log.warn("【安全闸】被挡次数写不进 Redis，这一轮的 {} 次不计, 键: {}", n, e.getKey(), ex);
            }
        }
    }
}
