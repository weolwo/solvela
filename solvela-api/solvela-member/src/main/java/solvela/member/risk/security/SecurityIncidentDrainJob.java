package solvela.member.risk.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import solvela.base.json.JsonUtils;
import solvela.base.module.jobspi.constant.SolvelaJobLaneEnum;
import solvela.base.module.jobspi.core.SolvelaJob;
import solvela.base.module.jobspi.core.SolvelaJobContext;
import solvela.base.module.jobspi.core.SolvelaJobHandler;
import solvela.member.risk.config.RiskListCache;
import solvela.member.risk.gateway.GatewaySnapshotPublisher;
import solvela.security.SecurityIncidentEvent;
import solvela.security.SecurityKeys;

import java.util.List;

/**
 * 每分钟把安全事件队列（{@link SecurityKeys#INCIDENT_QUEUE}）里的事件落库（网关方案 §6.4「写入链路」）。
 *
 * <h3>取走、处理完、再删</h3>
 * {@code RPOPLPUSH} 把一条从队列挪到「处理中」（{@link SecurityKeys#INCIDENT_PROCESSING}），落完库才从那里删掉。
 * 任务崩在半路，那一条还在「处理中」，下一次先把它处理掉 —— 不会丢。调度框架保证同一时刻只有一个在跑，
 * 所以「处理中」里的东西一定是上一次没做完的，不会和别人抢。
 *
 * <h3>处罚以 Redis 为准，这里只是留痕与名单</h3>
 * 落库晚一分钟，处罚早就生效了；这个任务停了几天，队列满了丢的是最老的事件，处罚本身不受影响。
 *
 * <h3>坏事件只丢那一条</h3>
 * JSON 解不开、数据不合法：打 ERROR 带原文，丢掉，接着处理下一条 —— 一条坏消息不该把后面的全堵住。
 * 数据库出错不一样：那条留在「处理中」，这一轮停下，下一轮重试。
 *
 * @Date 2026-10-17
 */
@Slf4j
@Component
@SolvelaJobHandler(
        name = "securityIncidentDrain",
        title = "【安全】安全事件落库",
        group = "SYSTEM",
        lane = SolvelaJobLaneEnum.FAST,
        idempotent = false,
        defaultTimeoutSeconds = 30
)
public class SecurityIncidentDrainJob implements SolvelaJob {

    /** 一轮最多处理这么多条、最多花这么久（FAST 通道的超时是 30 秒） */
    static final int MAX_PER_RUN = 5000;

    static final long BUDGET_MILLIS = 20_000;

    private final StringRedisTemplate redis;

    private final SecurityIncidentStore store;

    private final RiskListCache listCache;

    private final GatewaySnapshotPublisher snapshotPublisher;

    private final String queueKey;

    private final String processingKey;

    @Autowired
    public SecurityIncidentDrainJob(StringRedisTemplate redis, SecurityIncidentStore store, RiskListCache listCache,
                                    GatewaySnapshotPublisher snapshotPublisher) {
        this(redis, store, listCache, snapshotPublisher, SecurityKeys.INCIDENT_QUEUE, SecurityKeys.INCIDENT_PROCESSING);
    }

    /** 测试换一对键：不和本机网关真在投的那个队列抢 */
    SecurityIncidentDrainJob(StringRedisTemplate redis, SecurityIncidentStore store, RiskListCache listCache,
                             GatewaySnapshotPublisher snapshotPublisher, String queueKey, String processingKey) {
        this.redis = redis;
        this.store = store;
        this.listCache = listCache;
        this.snapshotPublisher = snapshotPublisher;
        this.queueKey = queueKey;
        this.processingKey = processingKey;
    }

    @Override
    public String execute(SolvelaJobContext ctx) {
        return drain().toString();
    }

    /** 一轮的结果 */
    public record Result(int stored, int recovered, int dropped, boolean listChanged, boolean stoppedOnDbError) {

        @Override
        public String toString() {
            return String.format("落库 %d 条（其中上次没做完的 %d 条），丢弃坏事件 %d 条%s%s", stored, recovered, dropped,
                    listChanged ? "，名单有变已重发网关快照" : "", stoppedOnDbError ? "；数据库出错，本轮提前结束" : "");
        }
    }

    public Result drain() {
        long deadline = System.currentTimeMillis() + BUDGET_MILLIS;
        int stored = 0, recovered = 0, dropped = 0;
        boolean changed = false;
        boolean dbError = false;

        // 先把上一次没做完的处理掉
        List<String> leftovers = redis.opsForList().range(processingKey, 0, -1);
        if (leftovers != null) {
            for (String raw : leftovers) {
                Handled h = handle(raw);
                if (h == Handled.DB_ERROR) {
                    dbError = true;
                    break;
                }
                if (h == Handled.DROPPED) {
                    dropped++;
                } else {
                    stored++;
                    recovered++;
                    changed |= h == Handled.LIST_CHANGED;
                }
            }
        }

        while (!dbError && stored + dropped < MAX_PER_RUN && System.currentTimeMillis() < deadline) {
            String raw = redis.opsForList().rightPopAndLeftPush(queueKey, processingKey);
            if (raw == null) {
                break;
            }
            Handled h = handle(raw);
            if (h == Handled.DB_ERROR) {
                dbError = true;
            } else if (h == Handled.DROPPED) {
                dropped++;
            } else {
                stored++;
                changed |= h == Handled.LIST_CHANGED;
            }
        }

        if (changed) {
            listCache.reload();
            snapshotPublisher.publishQuietly("安全事件更新了名单");
        }
        return new Result(stored, recovered, dropped, changed, dbError);
    }

    private enum Handled { STORED, LIST_CHANGED, DROPPED, DB_ERROR }

    private Handled handle(String raw) {
        SecurityIncidentEvent event;
        try {
            event = JsonUtils.parseObject(raw, SecurityIncidentEvent.class);
        } catch (RuntimeException e) {
            event = null;
        }
        if (event == null) {
            log.error("【安全事件】解不开的事件，丢弃: {}", SecurityIncidentEvent.truncate(raw, 512));
            redis.opsForList().remove(processingKey, 1, raw);
            return Handled.DROPPED;
        }
        try {
            boolean changed = store.store(event);
            redis.opsForList().remove(processingKey, 1, raw);
            return changed ? Handled.LIST_CHANGED : Handled.STORED;
        } catch (DataAccessException e) {
            log.error("【安全事件】落库失败，留在处理中等下一轮, 类型: {}, 规则: {}", event.incidentType(), event.ruleCode(), e);
            return Handled.DB_ERROR;
        } catch (RuntimeException e) {
            log.error("【安全事件】事件处理失败，丢弃: {}", SecurityIncidentEvent.truncate(raw, 512), e);
            redis.opsForList().remove(processingKey, 1, raw);
            return Handled.DROPPED;
        }
    }
}
