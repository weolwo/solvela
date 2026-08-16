package sa.base.module.support.job.core;

import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;

/**
 * 本节点「正在跑哪些执行记录」的登记表，用于支持后台的「终止」操作。
 *
 * <p>🔴 <b>没有它，任务跑飞了只能重启服务</b> —— 而重启会把所有正在跑的任务一起打断，
 * 为了砍一个失控任务牺牲另外十个，这是运维最不该被迫做的选择。
 *
 * <p>登记表是<b>节点本地</b>的：终止请求由 ADMIN 广播出去，
 * 只有真正持有那个 Future 的节点会响应，其余节点查无此条、安静忽略。
 * 这比维护一张全局「谁在跑什么」的表简单得多，也不会有一致性问题。
 *
 * <p>⚠️ <b>终止能不能生效取决于执行器有没有响应中断</b>
 * （见 {@link SmartJob} 的 javadoc）：{@code cancel(true)} 本质是
 * {@link Thread#interrupt()}。不响应中断的任务，点了终止也砍不掉 ——
 * 界面必须如实告诉运营「已发出中断信号」，而不是「已终止」。
 *
 * @author alaric
 * @date 2026-08-11
 */
@Slf4j
public class SmartJobRunningRegistry {

    private final Map<Long, Future<?>> runningMap = new ConcurrentHashMap<>();

    public void register(Long logId, Future<?> future) {
        if (null != logId && null != future) {
            runningMap.put(logId, future);
        }
    }

    /**
     * 🔴 必须在 finally 里调用，否则登记表会随执行次数无限增长
     */
    public void unregister(Long logId) {
        if (null != logId) {
            runningMap.remove(logId);
        }
    }

    /**
     * 尝试终止。
     *
     * @return false 表示本节点没在跑这条记录（正常情况 —— 它在别的节点上）
     */
    public boolean cancel(Long logId) {
        Future<?> future = runningMap.get(logId);
        if (null == future) {
            return false;
        }
        boolean cancelled = future.cancel(true);
        log.warn("==== SmartJob ==== 已对执行记录发出中断信号 logId={} cancel返回={}", logId, cancelled);
        return true;
    }

    public boolean isRunning(Long logId) {
        return runningMap.containsKey(logId);
    }

    public int size() {
        return runningMap.size();
    }
}
