package sa.base.module.support.job.log;

import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RList;
import org.redisson.api.RedissonClient;

import java.time.Duration;
import java.util.List;

/**
 * 执行日志采集：把某次执行期间打的日志收进 Redis，供后台「查看实时日志」。
 *
 * <p>🔴 <b>它解决的是排查定时任务时最痛的那件事：</b>
 * 任务执行到一半卡住时，{@code t_smart_job_log} 里只有<b>结束时</b>才写入的
 * {@code error_detail}，中间的 {@code log.info} 全在服务器滚动文件里 ——
 * 运营和开发看不到，只能登机器 grep，还得按时间猜是哪一段。
 *
 * <p><b>三个必须做的防护，缺一个都会反过来伤害系统：</b>
 * <ol>
 *   <li>🔴 <b>{@link #MAX_LINES} 上限 + LTRIM</b>：一个疯狂打日志的任务能写爆 Redis。
 *       超限后保留最新的若干行 —— 排查时最有价值的通常是<b>最后</b>那几行；</li>
 *   <li>🔴 <b>TTL</b>：这是排查用的临时数据，不是审计数据，过期即弃；</li>
 *   <li>🔴 <b>单行截断</b>：一条超长堆栈能顶掉整个缓冲区的额度。</li>
 * </ol>
 *
 * <p>写入是「best effort」：Redis 抖动绝不能影响任务本身 ——
 * <b>为了看日志而让任务失败是本末倒置</b>，所以这里所有异常都被吞掉并降级为一条 warn。
 *
 * @author alaric
 * @date 2026-08-11
 */
@Slf4j
public class SmartJobLogCollector {

    private static final String KEY_PREFIX = "smart-job-log:";

    /**
     * 单次执行最多保留的日志行数
     */
    private static final int MAX_LINES = 2000;

    private static final int MAX_LINE_LENGTH = 2000;

    private static final Duration TTL = Duration.ofDays(1);

    private final RedissonClient redissonClient;

    public SmartJobLogCollector(RedissonClient redissonClient) {
        this.redissonClient = redissonClient;
    }

    /**
     * 追加一行。由 Logback Appender 调用，<b>必须极快且绝不抛异常</b>
     */
    public void append(Long logId, String line) {
        if (null == logId || null == line) {
            return;
        }
        try {
            RList<String> list = redissonClient.getList(KEY_PREFIX + logId);
            list.add(line.length() > MAX_LINE_LENGTH ? line.substring(0, MAX_LINE_LENGTH) + "...(截断)" : line);
            // 超限时保留最新的 MAX_LINES 行：排查时最有价值的通常是最后那几行
            if (list.size() > MAX_LINES) {
                list.trim(list.size() - MAX_LINES, list.size() - 1);
            }
            list.expire(TTL);
        } catch (Exception e) {
            // 采集失败绝不能影响任务本身
            log.warn("==== SmartJob ==== 执行日志采集失败 logId={}", logId, e);
        }
    }

    /**
     * 读取某次执行的日志。取不到就是取不到（已过期或那台节点没开采集），返回空
     */
    public List<String> read(Long logId) {
        try {
            RList<String> list = redissonClient.getList(KEY_PREFIX + logId);
            return list.readAll();
        } catch (Exception e) {
            log.warn("==== SmartJob ==== 执行日志读取失败 logId={}", logId, e);
            return List.of();
        }
    }
}
