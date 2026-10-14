package solvela.member.risk.config;

import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/**
 * 一份「过期了就重载」的本地快照：规则表、名单都用它。
 *
 * <h3>为什么不用 {@code @Scheduled}</h3>
 * app-biz 与 admin 都刻意没开 {@code @EnableScheduling}（{@code BizApplication} 类注释：
 * 三个进程连同一个库，{@code @Scheduled} 每多开一个进程就多跑一遍）。这里是每个进程各自的本地缓存，
 * 本来就该每个进程各管各的 —— 但没必要为它开一个调度器：<b>读的时候发现过期了，顺手重载</b>。
 *
 * <h3>并发</h3>
 * 过期后只有一个线程去重载（{@code tryLock}），其余线程<b>继续用旧快照</b>，不排队等。
 * 重载失败（库抖了）保留旧快照、下次读再试 —— 名单和规则晚 30 秒生效，好过登录接口跟着库一起挂。
 *
 * @Date 2026-10-15
 */
@Slf4j
public final class PeriodicSnapshot<T> {

    private final String name;

    private final Supplier<T> loader;

    private final long intervalNanos;

    private final LongSupplier clock;

    private final ReentrantLock lock = new ReentrantLock();

    private volatile T value;

    private volatile long loadedAt;

    public PeriodicSnapshot(String name, Supplier<T> loader, Duration interval) {
        this(name, loader, interval, System::nanoTime);
    }

    PeriodicSnapshot(String name, Supplier<T> loader, Duration interval, LongSupplier clock) {
        this.name = name;
        this.loader = loader;
        this.intervalNanos = interval.toNanos();
        this.clock = clock;
    }

    /** 立即加载一次。启动时调用：失败直接抛出，让调用方决定要不要让启动失败 */
    public T loadNow() {
        T loaded = loader.get();
        value = loaded;
        loadedAt = clock.getAsLong();
        return loaded;
    }

    public T get() {
        T current = value;
        if (current == null) {
            // 还没加载过（启动期的竞态，或启动时加载失败了）：这一次同步加载，失败就抛
            lock.lock();
            try {
                return value != null ? value : loadNow();
            } finally {
                lock.unlock();
            }
        }
        if (clock.getAsLong() - loadedAt >= intervalNanos && lock.tryLock()) {
            try {
                if (clock.getAsLong() - loadedAt >= intervalNanos) {
                    loadNow();
                }
            } catch (RuntimeException e) {
                // 推迟下一次重试，免得库挂着的时候每个请求都去撞一次
                loadedAt = clock.getAsLong();
                log.warn("【风控】{} 重载失败，继续使用旧快照", name, e);
            } finally {
                lock.unlock();
            }
        }
        return value;
    }
}
