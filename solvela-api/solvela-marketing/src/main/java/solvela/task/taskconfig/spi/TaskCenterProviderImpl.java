package solvela.task.taskconfig.spi;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import solvela.activity.spi.TaskCenterProvider;
import solvela.enums.ActivityTypeEnum;
import solvela.enums.TaskConfigStatusEnum;
import solvela.marketing.api.TaskCenterItem;
import solvela.prize.prizeconfig.service.PrizeCatalog;
import solvela.prize.PrizeConfig;
import solvela.task.TaskConfig;
import solvela.task.TaskPrizeMapping;
import solvela.task.TaskRecord;
import solvela.task.prizemapping.manager.TaskPrizeMappingManager;
import solvela.task.record.manager.TaskRecordManager;
import solvela.task.runtime.TaskPeriodResolver;
import solvela.task.runtime.domain.TaskRuleConfig;
import solvela.task.taskconfig.manager.TaskConfigManager;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 任务玩法的任务中心查询。{@link TaskCenterProvider} 的唯一实现。
 *
 * <h3>🔴 全部批量查，一次都不逐条</h3>
 * 这是 C 端任务页每次进都会打的接口，一个活动挂十几个任务是常态。
 * 四类数据各查一次：任务配置、会员进度、档位映射、奖品名。
 * 逐条查任何一类都是 N+1，而它在开发环境（一个活动两三个任务）根本看不出来。
 *
 * <h3>没做过的任务也要给</h3>
 * 任务中心的价值恰恰是「告诉用户还有什么可做」。只回有进度记录的任务，
 * 用户第一次进来会看到一个空页面。
 */
@Component
@RequiredArgsConstructor
public class TaskCenterProviderImpl implements TaskCenterProvider {

    private final TaskConfigManager taskConfigManager;
    private final TaskRecordManager taskRecordManager;
    private final TaskPrizeMappingManager taskPrizeMappingManager;
    private final PrizeCatalog prizeCatalog;

    @Override
    public ActivityTypeEnum supportType() {
        return ActivityTypeEnum.TASK;
    }

    @Override
    public List<TaskCenterItem> listTasks(String activityCode, Long memberId) {
        /*
         * 🔴 判据是「status != 3 已下线」+ 时间窗，<b>不是 status == 2 生效中</b>。
         *
         * 这一条必须和运行态（TaskEventService.findSubscribedConfigs）逐字一致 ——
         * 那边的注释写着「全工程没有任何地方把 status 从 1 改成 2，wizardSubmit
         * 落的就是 1」。本方法原先判 == ACTIVE，于是任务中心<b>结构性永远为空</b>：
         * 运营配得再对也不会出现一条，而接口 200、日志干净、一点异常都没有。
         *
         * 比空更糟的是<b>两边判据不一致</b>时的样子：运行态判 != 3，所以进度照常在涨，
         * 而展示判 == 2，所以用户看不见 —— 他在完成一个不存在的任务。
         * 真要引入「草稿态不展示」，那就得连运行态一起改，否则又会分叉。
         *
         * 时间窗这里按 now() 判（运行态按事件发生时间判，那是为了让迟到的事件
         * 按它发生时的状态算）：没开始和已结束的任务对用户没有意义，
         * 显示出来只会让他点一个做不了的东西。
         */
        LocalDateTime now = LocalDateTime.now();
        List<TaskConfig> tasks = taskConfigManager.lambdaQuery()
                .eq(TaskConfig::getActivityCode, activityCode)
                .ne(TaskConfig::getStatus, TaskConfigStatusEnum.OFFLINE)
                .and(w -> w.isNull(TaskConfig::getStartTime).or().le(TaskConfig::getStartTime, now))
                .and(w -> w.isNull(TaskConfig::getEndTime).or().ge(TaskConfig::getEndTime, now))
                .list();
        if (tasks.isEmpty()) {
            return List.of();
        }

        List<Long> taskIds = tasks.stream().map(TaskConfig::getId).toList();
        Map<Long, TaskRecord> records = currentRecords(taskIds, memberId, tasks);
        Map<Long, String> rewards = rewardTexts(taskIds);

        return tasks.stream()
                .map(task -> toItem(task, records.get(task.getId()), rewards.get(task.getId())))
                // 排序权重是运营配的，按它排；权重相同按 id 兜底，保证顺序稳定
                .sorted(Comparator
                        .comparing((TaskCenterItem i) -> i.sortWeight() == null ? 0 : i.sortWeight())
                        .reversed()
                        .thenComparing(TaskCenterItem::taskId))
                .toList();
    }

    /**
     * 会员在<b>当前周期</b>的进度记录。
     *
     * <p>任务按 {@code period_key} 分片（每日任务每天一条记录），所以不能简单地
     * 「按 taskConfigId 取一条」—— 那样每日任务会取到昨天那条，进度看着没清零。
     * 这里按每个任务算出它此刻的 periodKey，再一次性把这些记录捞回来。
     */
    private Map<Long, TaskRecord> currentRecords(List<Long> taskIds, Long memberId,
                                                 List<TaskConfig> tasks) {
        if (memberId == null) {
            // 未登录：任务列表照常给（用户要看得到有什么可做），只是没有进度
            return Map.of();
        }
        LocalDateTime now = LocalDateTime.now();
        Set<String> periodKeys = tasks.stream()
                .map(task -> TaskPeriodResolver.resolvePeriodKey(
                        TaskRuleConfig.parse(task.getRuleConfig()).taskType(),
                        task.getLimitType(), now))
                .collect(Collectors.toSet());

        /*
         * 一次把「这些任务 × 这些周期」的记录全捞回来，再在内存里按 taskConfigId 归位。
         * periodKey 的种类很少（同一批任务通常只有 NONE 与今天两种），
         * 所以这个 IN 不会膨胀。
         */
        return taskRecordManager.lambdaQuery()
                .eq(TaskRecord::getMemberId, memberId)
                .in(TaskRecord::getTaskConfigId, taskIds)
                .in(TaskRecord::getPeriodKey, periodKeys)
                .list().stream()
                // 同一任务同一周期理论上只有一条；真出现多条时取进度最大的那条，
                // 不让展示随查询顺序抖动
                .collect(Collectors.toMap(TaskRecord::getTaskConfigId, Function.identity(),
                        (a, b) -> a.getCurrentMetric().compareTo(b.getCurrentMetric()) >= 0 ? a : b));
    }

    /**
     * 每个任务的奖励文案。
     *
     * <p>多档任务把各档奖品名用「/」连起来 —— 阶梯任务的价值就在于「做到 3 次得 A、
     * 5 次得 B」，只显示第一档等于把一半信息藏起来。
     *
     * <p>奖品名走 {@link PrizeCatalog#mapByCodes}（一次批量），不是逐个查。
     */
    private Map<Long, String> rewardTexts(List<Long> taskIds) {
        List<TaskPrizeMapping> mappings = taskPrizeMappingManager.lambdaQuery()
                .in(TaskPrizeMapping::getTaskConfigId, taskIds)
                .orderByAsc(TaskPrizeMapping::getStageLevel)
                .list();
        if (mappings.isEmpty()) {
            return Map.of();
        }
        Map<String, PrizeConfig> prizes = prizeCatalog.mapByCodes(
                mappings.stream().map(TaskPrizeMapping::getPrizeCode).collect(Collectors.toSet()));
        return mappings.stream().collect(Collectors.groupingBy(
                TaskPrizeMapping::getTaskConfigId,
                Collectors.mapping(mapping -> {
                    PrizeConfig prize = prizes.get(mapping.getPrizeCode());
                    // 奖品配置被删了：回显编码而不是空串 —— 空串会让整条任务看着没有奖励，
                    // 而那是运营配置的问题，不该表现成「这个任务白做」
                    return prize == null ? mapping.getPrizeCode() : prize.getPrizeName();
                }, Collectors.joining(" / "))));
    }

    private static TaskCenterItem toItem(TaskConfig task, TaskRecord record, String rewardText) {
        BigDecimal target = TaskRuleConfig.parse(task.getRuleConfig()).target();
        return new TaskCenterItem(
                task.getId(),
                task.getTaskName(),
                task.getTaskGroup(),
                target,
                // 没有记录 = 还没开始做，进度是 0 而不是 null
                record == null ? BigDecimal.ZERO : record.getCurrentMetric(),
                // 状态则保留 null：「还没开始」和「进行中 0 次」是两件事
                record == null ? null : record.getStatus(),
                rewardText,
                task.getActionUrl(),
                task.getSortWeight());
    }
}
