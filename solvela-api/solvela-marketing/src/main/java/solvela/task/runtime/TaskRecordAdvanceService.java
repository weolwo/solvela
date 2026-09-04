package solvela.task.runtime;

import solvela.enums.TaskRecordStatusEnum;
import solvela.enums.TaskFlowTypeEnum;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import solvela.base.json.JsonUtils;
import solvela.task.constant.TaskConst;
import solvela.task.constant.TaskDiscardCode;
import solvela.task.constant.TaskTypeEnum;
import solvela.task.TaskPrizeMapping;
import solvela.task.prizemapping.manager.TaskPrizeMappingManager;
import solvela.task.record.dao.TaskRecordDao;
import solvela.task.TaskRecord;
import solvela.task.recordflow.dao.TaskRecordFlowDao;
import solvela.task.TaskRecordFlow;
import solvela.task.runtime.domain.MetricPlan;
import solvela.task.runtime.domain.TaskAdvanceResult;
import solvela.task.runtime.domain.TaskEventContext;
import solvela.task.runtime.domain.TaskRuleConfig;
import solvela.task.runtime.strategy.TaskProgressStrategy;
import solvela.task.runtime.strategy.TaskProgressStrategyFactory;
import solvela.task.TaskConfig;
import solvela.task.TaskEvent;
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


    /**
     * 任务未配置结束时间时的兜底过期时间。
     *
     * <p>{@code valid_end_time} 是 NOT NULL 且无默认值（该模式在本项目已复发 5 次），
     * 必须显式赋值，否则 MyBatis-Plus 省略 null 字段 + MySQL 严格模式直接拒绝插入。
     * 取一个明确的远期值而不是 {@code null}，也让「永不过期」在数据上是可见的。
     */
    private static final LocalDateTime FOREVER = LocalDateTime.of(2099, 12, 31, 23, 59, 59);

    /**
     * 一次推进的运行态上下文：从「决定要推进这个任务」那一刻起就固定，之后整条链路共用。
     *
     * <p>它替掉的是一组在四五个私有方法之间原样传来传去的参数
     * （{@code config / ctx / rule / strategy / flow / logDiscard}）——
     * 每加一个推进级的东西，那几个签名要一起改，而漏改哪一个编译器不会提醒。
     * 与抽奖链路里的 {@code DrawBatch} 是同一个做法。
     *
     * @param flow       已经落库的事件流水。<b>它是可变的</b>：终态（推进量 / 丢弃原因）
     *                   在链路末端才补齐，见 {@link #finishAsDiscard} 与 {@link #finishFlow}
     * @param logDiscard 这个事件要不要留丢弃流水。丢弃流水是客诉自证的关键，
     *                   但高频事件每条不匹配都写一行会把流水表写爆，故由事件注册表开关控制
     */
    private record Advancing(TaskConfig config, TaskEventContext ctx, TaskRuleConfig rule,
                             TaskProgressStrategy strategy, TaskRecordFlow flow, boolean logDiscard) {
    }

    /**
     * 进度真的动了：这一笔之前是多少、之后是多少、动了多少。
     *
     * <p>三个数<b>必须一起产生</b>：阶梯发奖靠 {@code (before, after]} 这个左开右闭区间
     * 判断本次跨过了哪几档，流水靠 delta 与 after 对账。谁单独变了都对不上。
     */
    private record MetricChange(BigDecimal before, BigDecimal after, BigDecimal delta) {
    }

    /**
     * 推进一个任务配置的进度。
     *
     * <p>步骤顺序<b>不能调</b>：先插流水（占住幂等键）→ 再推进进度，两者同一事务。
     * 反过来会在两步之间留下「进度加了但没留痕」的窗口，重投时会重复累加。
     *
     * @throws TaskConcurrentModifyException STREAK 乐观锁冲突，调用方应重试
     */
    @Transactional(rollbackFor = Exception.class)
    public TaskAdvanceResult advance(TaskConfig config, TaskEventContext ctx, TaskEvent eventDef) {
        boolean logDiscard = eventDef == null || !Boolean.FALSE.equals(eventDef.getDiscardLogFlag());
        TaskRuleConfig rule = TaskRuleConfig.parse(config.getRuleConfig());

        // 配置异常直接落丢弃流水，不抛异常 —— 一个坏配置不该让整批事件处理中断
        TaskTypeEnum taskType = rule.taskType();
        if (taskType == null) {
            return discardWithoutRecord(config, ctx, TaskDiscardCode.CONFIG_INVALID,
                    "任务类型未配置或非法：rule_config.taskType="
                            + rule.string(TaskConst.RULE_KEY_TASK_TYPE), logDiscard);
        }
        TaskProgressStrategy strategy = strategyFactory.resolve(taskType);
        if (strategy == null) {
            return discardWithoutRecord(config, ctx, TaskDiscardCode.CONFIG_INVALID,
                    "没有支持 " + taskType.getValue() + " 的进度策略实现", logDiscard);
        }

        // 先插流水：幂等键先占住
        TaskRecordFlow flow = buildFlow(config, ctx);
        try {
            taskRecordFlowDao.insert(flow);
        } catch (DuplicateKeyException e) {
            // 撞 uk_t_tsk_flw_evt = 该事件对该任务配置已处理过。这是幂等生效，不是错误
            log.debug("[任务推进] 幂等命中，跳过。taskConfigId={}, memberId={}, eventBizId={}",
                    config.getId(), ctx.memberId(), ctx.eventBizId());
            return new TaskAdvanceResult.Duplicated(ctx.eventBizId());
        }

        Advancing advancing = new Advancing(config, ctx, rule, strategy, flow, logDiscard);
        AudienceCheck audience = checkAudience(config, ctx);
        if (audience != null) {
            return finishAsDiscard(advancing, audience.code(), audience.reason());
        }

        return switch (resolveRecord(advancing, taskType)) {
            case RecordLookup.Rejected rejected -> rejected.result();
            case RecordLookup.Found found -> applyProgress(advancing, found.record());
        };
    }

    /**
     * 找记录的两种结局：找到了可以推的，或者这一笔就到此为止。
     *
     * <p>用 sealed 而不是「返回 null 表示被拒」：被拒时的丢弃文案要原样返回给调用方，
     * 而它<b>未必写在流水上</b>（高频事件关掉留痕时那一行会被删掉），
     * 从 null 反推不出来。
     */
    private sealed interface RecordLookup {

        record Found(TaskRecord record) implements RecordLookup {
        }

        record Rejected(TaskAdvanceResult result) implements RecordLookup {
        }
    }

    /**
     * 取到本次要推进的那条任务记录（含 limit_count 轮次判定，必要时新建）。
     */
    private RecordLookup resolveRecord(Advancing adv, TaskTypeEnum taskType) {
        String basePeriodKey = TaskPeriodResolver.resolvePeriodKey(
                taskType, adv.config().getLimitType(), adv.ctx().eventTime());
        RoundResolution round = resolveRound(adv.config(), adv.ctx(), basePeriodKey);
        if (round.rejectReason() != null) {
            return new RecordLookup.Rejected(
                    finishAsDiscard(adv, TaskDiscardCode.ROUND_LIMIT_EXCEEDED, round.rejectReason()));
        }

        TaskRecord record = round.record() != null
                ? round.record()
                : getOrCreateRecord(adv.config(), adv.ctx(), round.periodKey());
        adv.flow().setRecordId(record.getId());

        if (record.getStatus() != TaskRecordStatusEnum.RUNNING) {
            return new RecordLookup.Rejected(finishAsDiscard(adv, TaskDiscardCode.RECORD_NOT_RUNNING,
                    "任务记录已不在进行中（status=" + record.getStatus() + "）"));
        }
        return new RecordLookup.Found(record);
    }

    /**
     * 算方案 -> 写进度 -> 阶梯发奖 -> 达标闸门 -> 流水补齐终态。
     *
     * <p>{@code strategy.plan} 是<b>纯函数，不碰库</b>：算出「该怎么推」与「真的去推」分开，
     * 四种任务类型的差异全部收在 plan 里，写库这一段所有类型共用。
     */
    private TaskAdvanceResult applyProgress(Advancing adv, TaskRecord record) {
        MetricPlan plan = adv.strategy().plan(record, adv.ctx(), adv.rule());

        MetricChange change;
        switch (plan) {
            case MetricPlan.Skip(TaskDiscardCode code, String reason) -> {
                return finishAsDiscard(adv, code, reason);
            }
            case MetricPlan.Accumulate(BigDecimal amount) -> {
                int rows = taskRecordDao.advanceMetric(record.getId(), amount);
                if (rows == 0) {
                    // rows=0 的语义是「记录已不可推进」（状态已流转），不是并发冲突，不需要重试
                    return finishAsDiscard(adv, TaskDiscardCode.RECORD_NOT_RUNNING,
                            "任务记录已不可推进（并发达标或已过期）");
                }
                // 重读权威值：条件更新拿不到结果值，且必须以 DB 为准。
                // 我的 UPDATE 持有行锁，after 里已包含所有先于我提交的增量，
                // 故 after - delta 恰好是「我这一笔之前」的值，并发下同样成立
                BigDecimal after = currentMetricOf(record.getId());
                change = new MetricChange(after.subtract(amount), after, amount);
            }
            case MetricPlan.Overwrite(BigDecimal metric, String progressJson, Integer expectedVersion) -> {
                int rows = taskRecordDao.overwriteMetric(record.getId(), metric, progressJson, expectedVersion);
                if (rows == 0) {
                    throw new TaskConcurrentModifyException("STREAK 乐观锁冲突，recordId=" + record.getId()
                            + ", version=" + expectedVersion);
                }
                BigDecimal before = record.getCurrentMetric() == null ? BigDecimal.ZERO : record.getCurrentMetric();
                change = new MetricChange(before, metric, metric.subtract(before));
                record.setProgressData(progressJson);
            }
        }

        record.setCurrentMetric(change.after());
        // 账号快照由上下文传给派发器：任务记录上没有这一列，而派发事件要带上展示名
        Integer highestReached = taskPrizeDispatcher.dispatchReachedStages(
                record, adv.ctx().memberName(), adv.config().getActivityCode(), change.before(), change.after());

        boolean completed = markCompletedIfReached(adv, record, change.after(), highestReached);
        finishFlow(adv.flow(), change);
        return new TaskAdvanceResult.Advanced(record.getId(), change.before(), change.after(), completed);
    }

    /**
     * 达标闸门。
     *
     * <p>用条件更新做并发闸门：只有一个线程能把 0 推到 1，避免重复触发完成后的动作。
     * 抢到的那个线程顺手流转到「已发奖」—— 最高档的奖已在上一步投递，
     * 防重由 {@code uk_external_biz} 保证。
     */
    private boolean markCompletedIfReached(Advancing adv, TaskRecord record,
                                           BigDecimal after, Integer highestReached) {
        boolean completed = adv.strategy().isCompleted(after, adv.rule())
                && isHighestStageReached(adv.config(), highestReached);
        if (!completed) {
            return false;
        }
        if (taskRecordDao.markCompleted(record.getId()) > 0) {
            taskRecordDao.markDispatched(record.getId());
            log.info("[任务达标] recordId={}, memberId={}, taskConfigId={}, metric={}",
                    record.getId(), adv.ctx().memberId(), adv.config().getId(), after.toPlainString());
        }
        return true;
    }

    /** 流水补齐终态：推进量与推进后的值 */
    private void finishFlow(TaskRecordFlow flow, MetricChange change) {
        flow.setFlowType(TaskFlowTypeEnum.ADVANCE);
        flow.setDeltaMetric(change.delta());
        flow.setAfterMetric(change.after());
        taskRecordFlowDao.updateById(flow);
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
     * 人群过滤：任务配了目标人群时，上游必须告知会员属性。
     *
     * <p>🔴 <b>上游没告知时丢弃，而不是放行。</b>
     * 放行等于「配了人群但对所有人生效」—— 运营看不出任何异常，
     * 直到有人问「为什么老会员也领到新人礼」才发现，那时奖已经发出去了。
     * 丢弃并写明原因，至少让它在事件流水里是可见的。
     *
     * @return null 表示通过；否则为丢弃原因
     */
    private record AudienceCheck(TaskDiscardCode code, String reason) {
    }

    private AudienceCheck checkAudience(TaskConfig config, TaskEventContext ctx) {
        String audience = config.getTargetAudience();
        if (audience == null || audience.isBlank() || TaskConst.AUDIENCE_ALL.equals(audience)) {
            return null;
        }
        boolean wantNew = TaskConst.AUDIENCE_NEW_MEMBER.equals(audience);
        boolean wantOld = TaskConst.AUDIENCE_OLD_MEMBER.equals(audience);
        if (!wantNew && !wantOld) {
            // 取值非法当作没配，放行 —— 为一个配错的枚举把用户的进度卡住不划算，
            // 但要留痕让人看得见（配置类问题一律 warn）
            log.warn("[任务推进] 目标人群取值非法，按不过滤处理。taskConfigId={}, targetAudience={}",
                    config.getId(), audience);
            return null;
        }
        if (ctx.isNewMember() == null) {
            return new AudienceCheck(TaskDiscardCode.AUDIENCE_UNKNOWN,
                    "任务限定了目标人群（" + audience + "），但上游未告知会员属性 isNewMember，无法判定，本次不计入");
        }
        if (wantNew && !ctx.isNewMember()) {
            return new AudienceCheck(TaskDiscardCode.AUDIENCE_MISMATCH, "该任务仅限新会员参与");
        }
        if (wantOld && ctx.isNewMember()) {
            return new AudienceCheck(TaskDiscardCode.AUDIENCE_MISMATCH, "该任务仅限老会员参与");
        }
        return null;
    }

    /**
     * 轮次判定结果。
     *
     * @param periodKey    本次该用的周期键（含轮次后缀）
     * @param record       已存在且仍可推进的记录；为 null 表示需要新建
     * @param rejectReason 不为 null 表示本周期轮次已用尽，事件应丢弃
     */
    private record RoundResolution(String periodKey, TaskRecord record, String rejectReason) {
    }

    /**
     * 按 {@code limit_count} 判定本次落在第几轮。
     *
     * <p>规则：本周期最新一轮还在进行中就继续用它；已完成则开下一轮，
     * 直到轮次用满 {@code limit_count}。
     *
     * <p>只有 DAILY / WEEKLY 受轮次限制（见 {@link TaskPeriodResolver#supportsRoundLimit}）。
     * 不受限时走原路径 —— 与改造前<b>完全等价</b>，存量任务行为不变。
     */
    private RoundResolution resolveRound(TaskConfig config, TaskEventContext ctx, String basePeriodKey) {
        int limitCount = config.getLimitCount() == null ? 1 : config.getLimitCount();
        if (!TaskPeriodResolver.supportsRoundLimit(config.getLimitType()) || limitCount <= 1) {
            return new RoundResolution(basePeriodKey, null, null);
        }

        TaskRecord latest = taskRecordDao.selectLatestRoundByPeriod(
                ctx.memberId(), config.getId(), basePeriodKey);
        if (latest == null) {
            return new RoundResolution(TaskPeriodResolver.withRound(basePeriodKey, 1), null, null);
        }
        if (latest.getStatus() == TaskRecordStatusEnum.RUNNING) {
            // 当前这一轮还没走完，继续推它
            return new RoundResolution(latest.getPeriodKey(), latest, null);
        }
        int currentRound = TaskPeriodResolver.parseRound(latest.getPeriodKey());
        if (currentRound >= limitCount) {
            return new RoundResolution(null, null,
                    "本周期参与次数已达上限（" + limitCount + " 次），下个周期才能继续");
        }
        return new RoundResolution(TaskPeriodResolver.withRound(basePeriodKey, currentRound + 1), null, null);
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
        TaskRecord existing = taskRecordDao.selectByUniqueKey(ctx.memberId(), config.getId(), periodKey);
        if (existing != null) {
            return existing;
        }
        TaskRecord record = buildRecord(config, ctx, periodKey);
        try {
            taskRecordDao.insert(record);
            return record;
        } catch (DuplicateKeyException e) {
            throw new TaskConcurrentModifyException("任务记录已被并发创建，换新事务重试。memberId="
                    + ctx.memberId() + ", taskConfigId=" + config.getId() + ", periodKey=" + periodKey);
        }
    }

    /**
     * 首次接取时建记录。
     *
     * <p>⚠️ 建实体前已对照过表里的 NOT NULL 且无默认值列
     * （member_id / task_config_id / activity_code / valid_start_time / valid_end_time /
     * rule_snapshot / prize_snapshot），全部显式赋值 ——
     * 该模式在本项目已复发 5 次，MyBatis-Plus 省略 null 字段 + MySQL 严格模式会直接拒绝插入。
     *
     * <p>两个快照是<b>规则变更不影响进行中的任务</b>的载体：运营改了规则，
     * 已接取的用户仍按接取时的规则算（与抽奖 DrawPrizeSnapshot、彩票奖级快照同一思路）。
     */
    private TaskRecord buildRecord(TaskConfig config, TaskEventContext ctx, String periodKey) {
        TaskRecord record = new TaskRecord();
        // 任务记录是状态表：只落关联键，不留账号快照（会员改名后快照会和主表长期不一致）
        record.setMemberId(ctx.memberId());
        record.setTaskConfigId(config.getId());
        record.setActivityCode(config.getActivityCode());
        record.setPeriodKey(periodKey);
        record.setValidStartTime(config.getStartTime() == null ? ctx.eventTime() : config.getStartTime());
        record.setValidEndTime(config.getEndTime() == null ? FOREVER : config.getEndTime());
        record.setCurrentMetric(BigDecimal.ZERO);
        record.setVersion(0);
        record.setStatus(TaskRecordStatusEnum.RUNNING);
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
        // 流水是单据：关联键 + 展示快照都落
        flow.setMemberId(ctx.memberId());
        flow.setMemberName(ctx.memberName());
        flow.setTaskConfigId(config.getId());
        flow.setEventCode(ctx.eventCode());
        flow.setEventBizId(ctx.eventBizId());
        flow.setFlowType(TaskFlowTypeEnum.ADVANCE);
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
    private TaskAdvanceResult finishAsDiscard(Advancing adv, TaskDiscardCode code, String reason) {
        TaskRecordFlow flow = adv.flow();
        if (!adv.logDiscard()) {
            // 高频事件关掉了丢弃留痕：把先前为占幂等键插入的那一行删掉。
            //
            // 删掉<b>不影响正确性</b>：被丢弃的事件没有产生任何副作用，重投时重新判定一次、
            // 依旧被丢弃，结果一样。而留着它反而有害 —— 高频事件的不匹配量级远大于匹配量级，
            // 一天就能把流水表写满。
            taskRecordFlowDao.deleteById(flow.getId());
            log.debug("[任务推进] 事件丢弃（该事件已关闭丢弃留痕）。taskConfigId={}, eventBizId={}, 原因={}",
                    flow.getTaskConfigId(), flow.getEventBizId(), reason);
            return new TaskAdvanceResult.Discarded(reason);
        }
        flow.setFlowType(TaskFlowTypeEnum.DISCARD);
        flow.setDiscardCode(code.getValue());
        flow.setDiscardReason(reason);
        taskRecordFlowDao.updateById(flow);
        return new TaskAdvanceResult.Discarded(reason);
    }

    /**
     * 连流水都还没插就判定丢弃（配置异常类）：补一条丢弃流水，仍要留痕。
     *
     * <p>幂等键撞了说明这个坏配置已经被记过一次，不必重复记录。
     */
    private TaskAdvanceResult discardWithoutRecord(TaskConfig config, TaskEventContext ctx,
                                                   TaskDiscardCode code, String reason, boolean logDiscard) {
        // 配置异常一律 warn：这类丢弃是「配错了」而不是「条件不满足」，即便关了流水也要看得见
        log.warn("[任务推进] 配置异常，事件丢弃。taskConfigId={}, reason={}", config.getId(), reason);
        if (!logDiscard) {
            return new TaskAdvanceResult.Discarded(reason);
        }
        TaskRecordFlow flow = buildFlow(config, ctx);
        flow.setFlowType(TaskFlowTypeEnum.DISCARD);
        flow.setDiscardCode(code.getValue());
        flow.setDiscardReason(reason);
        try {
            taskRecordFlowDao.insert(flow);
        } catch (DuplicateKeyException ignored) {
            // 同一事件重投，已记过一次
        }
        return new TaskAdvanceResult.Discarded(reason);
    }
}
