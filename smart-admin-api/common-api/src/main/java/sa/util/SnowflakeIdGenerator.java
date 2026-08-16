package sa.util;

/**
 * 雪花算法 ID 生成器。
 *
 * 移除 hutool 后接管 cn.hutool.core.lang.Snowflake。**位布局与起始纪元必须与 hutool 完全一致**：
 * 库里已经存了一批用 hutool 版本生成的 ID，改任何一个参数都会让新 ID 与历史 ID 落进同一段数值区间，
 * 从而有碰撞的可能，且 ID 不再随时间单调递增（很多地方隐含依赖这一点来排序）。
 *
 * <pre>
 *   1 位符号位（恒 0） | 41 位毫秒时间戳 | 5 位 dataCenterId | 5 位 workerId | 12 位序列号
 * </pre>
 *
 * 同一毫秒内最多 4096 个 ID，超出则自旋等到下一毫秒。
 *
 * ⚠️ 类名不叫 Snowflake：门面类 {@link SnowFlake} 与它只差一个字母的大小写，
 * 而 Windows 的文件系统大小写不敏感，两个源文件放不进同一个目录（本次改造真的踩到了，
 * 新文件直接把门面覆盖掉了）。
 *
 * @author 1024创新实验室
 */
public class SnowflakeIdGenerator {

    /**
     * 起始纪元，2010-11-04 09:42:54.657 UTC。
     * 🔴 与 hutool 的 DEFAULT_TWEPOCH 保持一致，不要改成「项目上线时间」之类看起来更合理的值 ——
     * 改了之后新生成的 ID 会比历史 ID 小，破坏单调性。
     */
    private static final long DEFAULT_TWEPOCH = 1288834974657L;

    /**
     * 容忍的时钟回拨毫秒数。NTP 校时经常有毫秒级抖动，回拨在这个范围内就按上一次的时间戳继续发号；
     * 超出则直接抛异常 —— 此时继续发号会真的重号，宁可让调用失败也不能静默产生重复 ID。
     */
    private static final long DEFAULT_TIME_OFFSET = 2000L;

    private static final long WORKER_ID_BITS = 5L;
    private static final long DATA_CENTER_ID_BITS = 5L;
    private static final long SEQUENCE_BITS = 12L;

    static final long MAX_WORKER_ID = ~(-1L << WORKER_ID_BITS);
    static final long MAX_DATA_CENTER_ID = ~(-1L << DATA_CENTER_ID_BITS);
    private static final long SEQUENCE_MASK = ~(-1L << SEQUENCE_BITS);

    private static final long WORKER_ID_SHIFT = SEQUENCE_BITS;
    private static final long DATA_CENTER_ID_SHIFT = SEQUENCE_BITS + WORKER_ID_BITS;
    private static final long TIMESTAMP_LEFT_SHIFT = SEQUENCE_BITS + WORKER_ID_BITS + DATA_CENTER_ID_BITS;

    private final long workerId;

    private final long dataCenterId;

    private long sequence = 0L;

    private long lastTimestamp = -1L;

    public SnowflakeIdGenerator(long workerId, long dataCenterId) {
        if (workerId > MAX_WORKER_ID || workerId < 0) {
            throw new IllegalArgumentException("workerId 必须在 0 ~ " + MAX_WORKER_ID + " 之间，实际为 " + workerId);
        }
        if (dataCenterId > MAX_DATA_CENTER_ID || dataCenterId < 0) {
            throw new IllegalArgumentException("dataCenterId 必须在 0 ~ " + MAX_DATA_CENTER_ID + " 之间，实际为 " + dataCenterId);
        }
        this.workerId = workerId;
        this.dataCenterId = dataCenterId;
    }

    /**
     * 生成下一个 ID。
     *
     * synchronized 不能去掉：sequence 与 lastTimestamp 是一对必须原子推进的状态，
     * 分开用两个原子变量维护会在「同一毫秒 + 序列回绕」的交叉点上发重号。
     */
    public synchronized long nextId() {
        long timestamp = System.currentTimeMillis();

        if (timestamp < lastTimestamp) {
            if (lastTimestamp - timestamp < DEFAULT_TIME_OFFSET) {
                timestamp = lastTimestamp;
            } else {
                throw new IllegalStateException(String.format(
                        "时钟回拨 %d 毫秒，超出容忍范围，拒绝发号", lastTimestamp - timestamp));
            }
        }

        if (timestamp == lastTimestamp) {
            sequence = (sequence + 1) & SEQUENCE_MASK;
            if (sequence == 0) {
                // 这一毫秒的 4096 个号用完了，等到下一毫秒
                timestamp = tilNextMillis(lastTimestamp);
            }
        } else {
            sequence = 0L;
        }

        lastTimestamp = timestamp;

        return ((timestamp - DEFAULT_TWEPOCH) << TIMESTAMP_LEFT_SHIFT)
                | (dataCenterId << DATA_CENTER_ID_SHIFT)
                | (workerId << WORKER_ID_SHIFT)
                | sequence;
    }

    private long tilNextMillis(long lastTimestamp) {
        long timestamp = System.currentTimeMillis();
        while (timestamp <= lastTimestamp) {
            timestamp = System.currentTimeMillis();
        }
        return timestamp;
    }

    /**
     * 拆出 ID 里的时间戳（毫秒），仅供测试与排查用
     */
    static long parseTimestamp(long id) {
        return (id >> TIMESTAMP_LEFT_SHIFT) + DEFAULT_TWEPOCH;
    }

    static long parseDataCenterId(long id) {
        return (id >> DATA_CENTER_ID_SHIFT) & MAX_DATA_CENTER_ID;
    }

    static long parseWorkerId(long id) {
        return (id >> WORKER_ID_SHIFT) & MAX_WORKER_ID;
    }

    static long parseSequence(long id) {
        return id & SEQUENCE_MASK;
    }
}
