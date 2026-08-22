package sa.task.runtime;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import sa.domain.event.UserPrizeEvent;
import sa.prize.prizeconfig.domain.entity.PrizeConfig;
import sa.prize.prizeconfig.service.PrizeConfigService;
import sa.task.constant.TaskConst;
import sa.task.prizemapping.domain.entity.TaskPrizeMapping;
import sa.task.prizemapping.manager.TaskPrizeMappingManager;
import sa.task.record.dao.TaskRecordDao;
import sa.task.record.domain.entity.TaskRecord;
import sa.task.runtime.domain.TaskProgressData;
import sa.task.runtime.domain.TaskRuleConfig;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 阶梯发奖：把「本次事件跨过了哪些档位」翻译成 {@link UserPrizeEvent}，复用既有派奖链路。
 *
 * <p>不另造一套派发 —— consumer → risk → ledger 那条链路已被抽奖压测与彩票 P4 端到端验证过
 * （防重、预算、审批、四种资产落账），任务模块只需把事件发对。
 *
 * <p>⚠️ 本类的方法<b>必须在调用方的事务内</b>执行：{@code UserPrizeEvent} 走
 * {@code @TransactionalEventListener(AFTER_COMMIT)}，<b>没有事务上下文事件根本不会投递</b>。
 * 彩票派奖那次就是这么表现为「记录都标成已投递了，但下游一条都没收到」（铁律 11 的同族）。
 *
 * @author alaric
 * @date 2026-08-01
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class TaskPrizeDispatcher {

    private final TaskPrizeMappingManager taskPrizeMappingManager;
    private final PrizeConfigService prizeConfigService;
    private final TaskRecordDao taskRecordDao;
    private final ApplicationEventPublisher applicationEventPublisher;

    /**
     * 派发本次事件跨过的所有档位。
     *
     * <p><b>判定窗口是 {@code (metricBefore, metricAfter]}</b>，只是一个优化 ——
     * 真正保证不重复发奖的是 {@code t_prize_log.uk_external_biz}（幂等键 recordId:stageLevel）。
     * 窗口在并发下可能算得不精确，但那没关系：多算了会被唯一索引挡下，
     * 而<b>判据只留唯一索引这一个</b>，两个判据一定会漂移（本项目已因「同一份数据两种判法」踩过坑）。
     *
     * @return 本次已达标的最高档位；一个都没跨过返回 null
     */
    public Integer dispatchReachedStages(TaskRecord record, String memberName, String activityCode,
                                         BigDecimal metricBefore, BigDecimal metricAfter) {
        List<TaskPrizeMapping> mappings = taskPrizeMappingManager.lambdaQuery()
                .eq(TaskPrizeMapping::getTaskConfigId, record.getTaskConfigId())
                .list();
        if (mappings.isEmpty()) {
            log.warn("[任务发奖] 任务未配置任何奖励档位，recordId={}, taskConfigId={}",
                    record.getId(), record.getTaskConfigId());
            return null;
        }
        mappings.sort(Comparator.comparing(TaskPrizeMapping::getStageLevel));

        List<Integer> dispatched = new ArrayList<>();
        Integer highestReached = null;

        for (TaskPrizeMapping mapping : mappings) {
            BigDecimal target = resolveStageTarget(mapping);
            if (target == null) {
                log.warn("[任务发奖] 档位达标条件解析失败，跳过。mappingId={}, stageCondition={}",
                        mapping.getId(), mapping.getStageCondition());
                continue;
            }
            // 已达标（带容差，铁律 1）
            boolean reachedNow = metricAfter.add(TaskRuleConfig.TOLERANCE).compareTo(target) >= 0;
            if (!reachedNow) {
                continue;
            }
            highestReached = mapping.getStageLevel();

            // 上一次就已经跨过的档位不重复投递（优化，非判据）
            boolean reachedBefore = metricBefore.add(TaskRuleConfig.TOLERANCE).compareTo(target) >= 0;
            if (reachedBefore) {
                continue;
            }
            if (publishStagePrize(record, memberName, activityCode, mapping)) {
                dispatched.add(mapping.getStageLevel());
            }
        }

        if (!dispatched.isEmpty()) {
            recordDispatchedStages(record, dispatched);
        }
        return highestReached;
    }

    /**
     * 发一个档位的奖。
     *
     * <p>🔴 {@code sourceBizId} 必须带档位 —— 详见 {@link TaskConst#buildSourceBizId}。
     * 只传 recordId 的话，阶梯任务第二档起会被 {@code PrizeDispatchHandler} 的
     * {@code catch (DuplicateKeyException)} 当作重复派发静默吞掉，零并发下必现。
     */
    private boolean publishStagePrize(TaskRecord record, String memberName, String activityCode,
                                      TaskPrizeMapping mapping) {
        PrizeConfig prizeConfig = prizeConfigService.getByActivityCodeAndPrizeCode(activityCode, mapping.getPrizeCode());
        if (prizeConfig == null) {
            log.error("[任务发奖] 奖品配置不存在，跳过派发。activityCode={}, prizeCode={}, recordId={}, stage={}",
                    activityCode, mapping.getPrizeCode(), record.getId(), mapping.getStageLevel());
            return false;
        }

        UserPrizeEvent event = UserPrizeEvent.builder()
                .sourceBizId(TaskConst.buildSourceBizId(record.getId(), mapping.getStageLevel()))
                .activityCode(activityCode)
                // 关联键取记录上的 member_id；展示名由上下文传进来 —— 任务记录是状态表，没有账号快照
                .memberId(record.getMemberId())
                .memberName(memberName)
                .prizeCode(mapping.getPrizeCode())
                .prizeType(prizeConfig.getPrizeType())
                .prizeValue(resolvePrizeValue(mapping, prizeConfig))
                .prizeName(prizeConfig.getPrizeName())
                .prizeLevel(mapping.getStageLevel())
                .build();
        applicationEventPublisher.publishEvent(event);

        log.info("[任务发奖] 已投递派发事件。recordId={}, stage={}, prizeCode={}, sourceBizId={}",
                record.getId(), mapping.getStageLevel(), mapping.getPrizeCode(), event.getSourceBizId());
        return true;
    }

    /**
     * 奖励值：FIXED 取 prize_strategy.value；解析不出则退回奖品配置的 prize_value。
     *
     * <p>RATIO / FORMULA 两种计算类型暂未实现，落到同一条退路上 ——
     * 刻意不在这里现算比例：{@code prize_mode} 的语义属于发奖策略层，
     * 在派发入口临时实现一份，将来必然与真正的策略实现漂移。
     */
    private String resolvePrizeValue(TaskPrizeMapping mapping, PrizeConfig prizeConfig) {
        if (TaskConst.PRIZE_MODE_FIXED.equals(mapping.getPrizeMode())) {
            BigDecimal configured = readDecimal(mapping.getPrizeStrategy(), TaskConst.PRIZE_STRATEGY_KEY_VALUE);
            if (configured != null) {
                return configured.toPlainString();
            }
        }
        return prizeConfig.getPrizeValue() == null ? null : prizeConfig.getPrizeValue().toPlainString();
    }

    /**
     * 达标条件目标值：{@code {"target": 3}}，由 wizardSubmit 写入
     */
    private BigDecimal resolveStageTarget(TaskPrizeMapping mapping) {
        return readDecimal(mapping.getStageCondition(), TaskConst.STAGE_KEY_TARGET);
    }

    /**
     * 子表两个 json 列的取值口径。管理端的巡检页（{@code TaskPrizeMappingService}）读的是同一条路径 ——
     * 各写一份「差不多的」解析，迟早出现「页面显示正常、运行态解析不出」的漂移。
     */
    private BigDecimal readDecimal(String json, String key) {
        return TaskRuleConfig.parse(json).decimal(key);
    }

    /**
     * 回写已派发档位。
     *
     * <p>⚠️ 这是<b>展示字段</b>，出错只记日志、不打断派发 ——
     * 它不是防重判据（判据是 uk_external_biz），写失败最多让运营界面少显示一格，
     * 而让它把已经投递成功的派发拖进回滚才是真正的损失。
     */
    private void recordDispatchedStages(TaskRecord record, List<Integer> stages) {
        try {
            TaskProgressData progress = TaskProgressData.parse(record.getProgressData());
            for (Integer stage : stages) {
                progress = progress.withDispatchedStage(stage);
            }
            String json = progress.toJson();
            taskRecordDao.updateProgressData(record.getId(), json);
            record.setProgressData(json);
        } catch (RuntimeException e) {
            log.warn("[任务发奖] 回写 dispatchedStages 失败（不影响派发，它只是展示字段）。recordId={}",
                    record.getId(), e);
        }
    }
}
