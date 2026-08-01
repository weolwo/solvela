package net.lab1024.sa.task.runtime;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.lab1024.sa.base.common.util.JsonUtils;
import net.lab1024.sa.task.constant.TaskConst;
import net.lab1024.sa.task.constant.TaskTypeEnum;
import net.lab1024.sa.task.prizemapping.domain.entity.TaskPrizeMapping;
import net.lab1024.sa.task.prizemapping.manager.TaskPrizeMappingManager;
import net.lab1024.sa.task.record.dao.TaskRecordDao;
import net.lab1024.sa.task.record.domain.entity.TaskRecord;
import net.lab1024.sa.task.recordflow.dao.TaskRecordFlowDao;
import net.lab1024.sa.task.recordflow.domain.entity.TaskRecordFlow;
import net.lab1024.sa.task.runtime.domain.MetricPlan;
import net.lab1024.sa.task.runtime.domain.TaskAdvanceResult;
import net.lab1024.sa.task.runtime.domain.TaskEventContext;
import net.lab1024.sa.task.runtime.domain.TaskRuleConfig;
import net.lab1024.sa.task.runtime.strategy.TaskProgressStrategy;
import net.lab1024.sa.task.runtime.strategy.TaskProgressStrategyFactory;
import net.lab1024.sa.task.taskconfig.domain.entity.TaskConfig;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 单个任务配置的进度推进 —— <b>运行态的核心，也是唯一写库的地方</b>。
 *
 * <p>🔴 <b>为什么它必须是独立 Bean，不能内联回 {@link TaskEventService}</b>：
 * {@code @Transactional} 靠 Spring AOP 代理生效，<b>同类内部的自调用不经过代理</b> ——
 * 把本方法写成 TaskEventService 的私有方法再从「循环处理多个匹配到的任务配置」里调，
 * 注解会<b>静默失效</b>：没有事务、没有报错、编译和单测全都通过，
 * 只在「进度加了但发奖没触发」的那一刻才暴露（铁律 11，本项目已连踩两次）。
 *
 * <p>拆开还有两个必需的好处：
 * ① <b>每个任务一个独立事务</b> —— 一个任务配置坏掉不会把同一批的其它任务一起回滚；
 * ② <b>乐观锁重试的每次尝试都是全新事务</b> —— 见 {@link TaskConcurrentModifyException}。
 *
 * <p>事务边界：DB 写入（流水 + 进度 + 状态）同一事务；{@code UserPrizeEvent} 在事务内
 * publish、经 {@code @TransactionalEventListener(AFTER_COMMIT)} 在<b>提交后</b>才真正派发，
 * 天然防「事务回滚但奖已发」。<b>没有事务上下文事件根本不会投递</b>，所以派发调用不能挪到方法外。
 *
 * @author alaric
 * @date 2026-08-01
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class TaskRecordAdvanceService {

    private final TaskRecordDao taskRecordDao;
    private final TaskRecordFlowDao taskRecordFlowDao;
    private final TaskPrizeMappingManager taskPrizeMappingManager;
    private final TaskProgressStrategyFactory strategyFactory;
    private final TaskPrizeDispatcher taskPrizeDispatcher;

    private static final String DEFAULT_TENANT_ID = "0";

    /**
     * 任务未配置结束时间时的兜底过期时间。
     *
     * <p>{@code valid_end_time} 是 NOT NULL 且无默认值（该模式在本项目已复发 5 次），
     * 必须显式赋值，否则 MyBatis-Plus 省略 null 字段 + MySQL 严格模式直接拒绝插入。
     * 取一个明确的远期值而不是 {@code null}，也让「永不过期」在数据上是可见的。
     */
    private static final LocalDateTime FOREVER = LocalDateTime.of(2099, 12, 31, 23, 59, 59);

    /**
     * 推进一个任务配置的进度。
     *
     * <p>步骤顺序<b>不能调</b>：先插流水（占住幂等键）→ 再推进进度，两者同一事务。
     * 反过来会在两步之间留下「进度加了但没留痕」的窗口，重投时会重复累加。
     *
     * @throws TaskConcurrentModifyException STREAK 乐观锁冲突，调用方应重试
     */
    @Transactional(rollbackFor = Exception.class)
    public TaskAdvanceResult advance(TaskConfig config, TaskEventContext ctx) {
        TaskRuleConfig rule = TaskRuleConfig.parse(config.getRuleConfig());
        TaskTypeEnum taskType = rule.taskType();
        if (taskType == null) {
            // 配置异常直接落丢弃流水，不抛异常 —— 一个坏配置不该让整批事件处理中断
            return discardWithoutRecord(config, ctx, "任务类型未配置或非法：rule_config.taskType="
                    + rule.string(TaskConst.RULE_KEY_TASK_TYPE));
        }
        TaskProgressStrategy strategy = strategyFactory.resolve(taskType);
        if (strategy == null) {
            return discardWithoutRecord(config, ctx, "没有支持 " + taskType.getValue() + " 的进度策略实现");
        }

        String periodKey = TaskPeriodResolver.resolvePeriodKey(taskType, config.getLimitType(), ctx.eventTime());

        // ========== ① 先插流水：幂等键先占住 ==========
        TaskRecordFlow flow = buildFlow(config, ctx);
        try {
            taskRecordFlowDao.insert(flow);
        } catch (DuplicateKeyException e) {
            // 撞 uk_t_tsk_flw_evt = 该事件对该任务配置已处理过。这是幂等生效，不是错误
            log.debug("[任务推进] 幂等命中，跳过。taskConfigId={}, member={}, eventBizId={}",
                    config.getId(), ctx.memberName(), ctx.eventBizId());
            return new TaskAdvanceResult.Duplicated(ctx.eventBizId());
        }

        // ========== ② 取或建任务记录 ==========
        TaskRecord record = getOrCreateRecord(config, ctx, periodKey);
        flow.setRecordId(record.getId());

        if (!Integer.valueOf(TaskConst.RECORD_STATUS_RUNNING).equals(record.getStatus())) {
            return finishAsDiscard(flow, "任务记录已不在进行中（status=" + record.getStatus() + "）");
        }

        // ========== ③ 策略算出推进方案（纯函数，不碰库） ==========
        MetricPlan plan = strategy.plan(record, ctx, rule);

        BigDecimal before;
        BigDecimal after;
        BigDecimal delta;

        switch (plan) {
            case MetricPlan.Skip(String reason) -> {
                return finishAsDiscard(flow, reason);
            }
            case MetricPlan.Accumulate(BigDecimal amount) -> {
                int rows = taskRecordDao.advanceMetric(record.getId(), amount);
                if (rows == 0) {
                    // rows=0 的语义是「记录已不可推进」（状态已流转），不是并发冲突，不需要重试
                    return finishAsDiscard(flow, "任务记录已不可推进（并发达标或已过期）");
                }
                delta = amount;
                // 重读权威值：条件更新拿不到结果值，且必须以 DB 为准
                after = currentMetricOf(record.getId());
                // 我的 UPDATE 持有行锁，after 里已包含所有先于我提交的增量，
                // 故 after - delta 恰好是「我这一笔之前」的值，并发下同样成立
                before = after.subtract(delta);
            }
            case MetricPlan.Overwrite(BigDecimal metric, String progressJson, Integer expectedVersion) -> {
                int rows = taskRecordDao.overwriteMetric(record.getId(), metric, progressJson, expectedVersion);
                if (rows == 0) {
                    throw new TaskConcurrentModifyException("STREAK 乐观锁冲突，recordId=" + record.getId()
                            + ", version=" + expectedVersion);
                }
                before = record.getCurrentMetric() == null ? BigDecimal.ZERO : record.getCurrentMetric();
                after = metric;
                delta = after.subtract(before);
                record.setProgressData(progressJson);
            }
        }

        // ========== ④ 阶梯发奖：本次跨过的档位 ==========
        record.setCurrentMetric(after);
        Integer highestReached = taskPrizeDispatcher.dispatchReachedStages(
                record, config.getActivityCode(), before, after);

        // ========== ⑤ 达标闸门 ==========
        boolean completed = strategy.isCompleted(after, rule) && isHighestStageReached(config, highestReached);
        if (completed) {
            // 条件更新做并发闸门：只有一个线程能把 0 推到 1，避免重复触发完成后的动作
            int rows = taskRecordDao.markCompleted(record.getId());
            if (rows > 0) {
                // 最高档的奖已在上一步投递（防重由 uk_external_biz 保证），可以直接流转到「已发奖」
                taskRecordDao.markDispatched(record.getId());
                log.info("[任务达标] recordId={}, member={}, taskConfigId={}, metric={}",
                        record.getId(), ctx.memberName(), config.getId(), after.toPlainString());
            }
        }

        // ========== ⑥ 流水补齐终态 ==========
        flow.setFlowType(TaskConst.FLOW_TYPE_ADVANCE);
        flow.setDeltaMetric(delta);
        flow.setAfterMetric(after);
        taskRecordFlowDao.updateById(flow);

        return new TaskAdvanceResult.Advanced(record.getId(), before, after, completed);
    }

    /**
     * 最高档是否已达标。
     *
     * <p>{@code status=1 已完成} 的语义是<b>最高档达标</b>（方案 §4.8）——
     * 阶梯任务里「第 1 档已发、第 2 档进行中」必须留在 {@code status=0}，
     * 否则 {@code advanceMetric} 的 {@code WHERE status=0} 会把后续事件全部挡住，
     * 表现是「拿了第一档奖之后任务就再也不动了」。
     */
    private boolean isHighestStageReached(TaskConfig config, Integer highestReached) {
        if (highestReached == null) {
            return false;
        }
        List<TaskPrizeMapping> mappings = taskPrizeMappingManager.lambdaQuery()
                .eq(TaskPrizeMapping::getTaskConfigId, config.getId()).list();
        if (mappings.isEmpty()) {
            // 没配奖励的任务，达标判定只看 rule_config
            return true;
        }
        int maxStage = mappings.stream().map(TaskPrizeMapping::getStageLevel)
                .filter(java.util.Objects::nonNull).mapToInt(Integer::intValue).max().orElse(0);
        return highestReached >= maxStage;
    }

    /**
     * 取或建任务记录。
     *
     * <p>并发下多个不同事件会同时为同一 (member, config, period) 建记录，
     * 由 {@code uk_t_tsk_rec_mbr_cfg_prd} 挡住后来者。
     *
     * <p>🔴 <b>撞索引后不能「就地重查」，必须换一个事务重来。</b>
     * 这是实测踩到的（10 并发只推进了 1 笔，另外 9 笔全抛 DuplicateKeyException）：
     * MySQL 默认隔离级别是 <b>REPEATABLE READ</b>，本事务的一致性视图在<b>第一次读</b>时就固定了 ——
     * 也就是上面那次返回 null 的 {@code selectByUniqueKey}。
     * 赢家随后才提交，它插入的那一行<b>不在本事务的快照里</b>，
     * 于是「INSERT 报重复 → 重查却查不到」这种看起来自相矛盾的现象就成立了。
     *
     * <p>两条可选修法，选了后者：
     * <ul>
     *   <li>把重查改成 {@code SELECT ... FOR UPDATE}（当前读能看到最新已提交版本）——
     *       能work，但为一个纯读场景引入行锁，且锁的是别人刚插入的行；</li>
     *   <li><b>抛 {@link TaskConcurrentModifyException} 让整个事务回滚、在新事务里重试</b> ——
     *       新事务有新快照，自然能读到。顺带把已插入的流水一并回滚，重试时幂等键可以重新占位，
     *       不会出现「流水占了坑但进度没推」的半截状态。</li>
     * </ul>
     */
    private TaskRecord getOrCreateRecord(TaskConfig config, TaskEventContext ctx, String periodKey) {
        TaskRecord existing = taskRecordDao.selectByUniqueKey(ctx.memberName(), config.getId(), periodKey);
        if (existing != null) {
            return existing;
        }
        TaskRecord record = buildRecord(config, ctx, periodKey);
        try {
            taskRecordDao.insert(record);
            return record;
        } catch (DuplicateKeyException e) {
            throw new TaskConcurrentModifyException("任务记录已被并发创建，换新事务重试。member="
                    + ctx.memberName() + ", taskConfigId=" + config.getId() + ", periodKey=" + periodKey);
        }
    }

    /**
     * 首次接取时建记录。
     *
     * <p>⚠️ 建实体前已对照过表里的 NOT NULL 且无默认值列
     * （member_name / task_config_id / activity_code / valid_start_time / valid_end_time /
     * rule_snapshot / prize_snapshot），全部显式赋值 ——
     * 该模式在本项目已复发 5 次，MyBatis-Plus 省略 null 字段 + MySQL 严格模式会直接拒绝插入。
     *
     * <p>两个快照是<b>规则变更不影响进行中的任务</b>的载体：运营改了规则，
     * 已接取的用户仍按接取时的规则算（与抽奖 DrawPrizeSnapshot、彩票奖级快照同一思路）。
     */
    private TaskRecord buildRecord(TaskConfig config, TaskEventContext ctx, String periodKey) {
        TaskRecord record = new TaskRecord();
        record.setTenantId(DEFAULT_TENANT_ID);
        record.setMemberName(ctx.memberName());
        record.setTaskConfigId(config.getId());
        record.setActivityCode(config.getActivityCode());
        record.setPeriodKey(periodKey);
        record.setValidStartTime(config.getStartTime() == null ? ctx.eventTime() : config.getStartTime());
        record.setValidEndTime(config.getEndTime() == null ? FOREVER : config.getEndTime());
        record.setCurrentMetric(BigDecimal.ZERO);
        record.setVersion(0);
        record.setStatus(TaskConst.RECORD_STATUS_RUNNING);
        record.setProgressData(JsonUtils.toJson(java.util.Map.of()));
        record.setRuleSnapshot(config.getRuleConfig() == null ? "{}" : config.getRuleConfig());
        record.setPrizeSnapshot(buildPrizeSnapshot(config.getId()));
        // create_time / update_time 由 DDL 的 DEFAULT CURRENT_TIMESTAMP 产生，代码里不要填（铁律 9）
        return record;
    }

    private String buildPrizeSnapshot(Long taskConfigId) {
        List<TaskPrizeMapping> mappings = taskPrizeMappingManager.lambdaQuery()
                .eq(TaskPrizeMapping::getTaskConfigId, taskConfigId).list();
        List<java.util.Map<String, Object>> snapshot = mappings.stream()
                .sorted(java.util.Comparator.comparing(TaskPrizeMapping::getStageLevel,
                        java.util.Comparator.nullsFirst(Integer::compareTo)))
                .map(m -> {
                    java.util.Map<String, Object> item = new java.util.LinkedHashMap<String, Object>();
                    item.put("stageLevel", m.getStageLevel());
                    item.put("prizeCode", m.getPrizeCode());
                    item.put("prizeMode", m.getPrizeMode());
                    item.put("stageCondition", m.getStageCondition());
                    item.put("prizeStrategy", m.getPrizeStrategy());
                    return item;
                }).toList();
        return JsonUtils.toJson(snapshot);
    }

    private BigDecimal currentMetricOf(Long recordId) {
        TaskRecord fresh = taskRecordDao.selectById(recordId);
        return fresh == null || fresh.getCurrentMetric() == null ? BigDecimal.ZERO : fresh.getCurrentMetric();
    }

    private TaskRecordFlow buildFlow(TaskConfig config, TaskEventContext ctx) {
        TaskRecordFlow flow = new TaskRecordFlow();
        flow.setTenantId(DEFAULT_TENANT_ID);
        flow.setMemberName(ctx.memberName());
        flow.setTaskConfigId(config.getId());
        flow.setEventCode(ctx.eventCode());
        flow.setEventBizId(ctx.eventBizId());
        flow.setFlowType(TaskConst.FLOW_TYPE_ADVANCE);
        flow.setDeltaMetric(BigDecimal.ZERO);
        flow.setAfterMetric(BigDecimal.ZERO);
        flow.setEventPayload(JsonUtils.toJson(ctx.payload()));
        return flow;
    }

    /**
     * 流水已落库，改判为丢弃并写明原因。
     *
     * <p>丢弃原因就是「用户下了 99 元的单为什么没进度」这类客诉的答案，必须是人话。
     */
    private TaskAdvanceResult finishAsDiscard(TaskRecordFlow flow, String reason) {
        flow.setFlowType(TaskConst.FLOW_TYPE_DISCARD);
        flow.setDiscardReason(reason);
        taskRecordFlowDao.updateById(flow);
        return new TaskAdvanceResult.Discarded(reason);
    }

    /**
     * 连流水都还没插就判定丢弃（配置异常类）：补一条丢弃流水，仍要留痕。
     *
     * <p>幂等键撞了说明这个坏配置已经被记过一次，不必重复记录。
     */
    private TaskAdvanceResult discardWithoutRecord(TaskConfig config, TaskEventContext ctx, String reason) {
        log.warn("[任务推进] 配置异常，事件丢弃。taskConfigId={}, reason={}", config.getId(), reason);
        TaskRecordFlow flow = buildFlow(config, ctx);
        flow.setFlowType(TaskConst.FLOW_TYPE_DISCARD);
        flow.setDiscardReason(reason);
        try {
            taskRecordFlowDao.insert(flow);
        } catch (DuplicateKeyException ignored) {
            // 同一事件重投，已记过一次
        }
        return new TaskAdvanceResult.Discarded(reason);
    }
}
