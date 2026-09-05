package solvela.task.taskconfig.spi;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import solvela.activity.spi.TaskCenterProvider;
import solvela.enums.ActivityTypeEnum;
import solvela.enums.TaskConfigStatusEnum;
import solvela.marketing.api.TaskCenterItem;
import solvela.marketing.api.TaskStageView;
import solvela.prize.prizeconfig.service.PrizeCatalog;
import solvela.prize.PrizeConfig;
import solvela.task.TaskConfig;
import solvela.task.TaskPrizeMapping;
import solvela.task.TaskRecord;
import solvela.task.constant.TaskConst;
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
        Map<Long, List<Stage>> stages = stagesOf(taskIds);

        return tasks.stream()
                .map(task -> toItem(task, records.get(task.getId()),
                        stages.getOrDefault(task.getId(), List.of())))
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
    private Map<Long, List<Stage>> stagesOf(List<Long> taskIds) {
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
                    String reward = prize == null ? mapping.getPrizeCode() : prize.getPrizeName();
                    /*
                     * 🔴 阈值取 stage_condition，不是 rule_config.targetCount。
                     * 那一列才是 TaskPrizeDispatcher 发奖时真正判的东西 ——
                     * 展示的判据必须和发放的判据同源，否则运营把档位改成 1/3/7
                     * 而没动 targetCount 时，进度条会满了却不发奖。
                     */
                    BigDecimal target = TaskRuleConfig.parse(mapping.getStageCondition())
                            .decimal(TaskConst.STAGE_KEY_TARGET);
                    return new Stage(mapping.getStageLevel() == null ? 1 : mapping.getStageLevel(),
                            target, reward);
                }, Collectors.toList())));
    }

    /**
     * 规则一句话。<b>由域侧拼，不是前端拼。</b>
     *
     * <p>taskType / tolerance 是域里的字典，前端照着拼就是第二份规则表 ——
     * 域里加一种玩法时它会静默说错话。与「状态文案由后端给」同一条理由。
     *
     * <p>用 switch 表达式且<b>不写 default</b>：{@code TaskTypeEnum} 新增取值时
     * 这里编译不过，而不是悄悄落进一句放之四海皆准的废话。
     */
    private static String ruleText(TaskConfig task) {
        TaskRuleConfig rule = TaskRuleConfig.parse(task.getRuleConfig());
        BigDecimal target = rule.target();
        String amount = target == null ? "?" : target.stripTrailingZeros().toPlainString();
        return switch (rule.taskType()) {
            case SIMPLE -> "完成一次即达标";
            case COUNT -> "累计完成 " + amount + " 次";
            case AMOUNT -> "累计金额满 " + amount;
            case STREAK -> {
                int tolerance = rule.tolerance();
                // 容错次数是连续型独有的关键信息：断一次到底会不会清零，用户要知道
                yield tolerance <= 0
                        ? "连续完成 " + amount + " 次，中断即清零"
                        : "连续完成 " + amount + " 次，最多可断 " + tolerance + " 次";
            }
        };
    }

    /**
     * 周期。<b>字典在域里</b>（TaskConst.LIMIT_*），前端不该认识这些字符串。
     *
     * <p>认不出的取值原样返回而不是编一句话 —— 那是配置里出现了字典外的值，
     * 显示原文至少让人看得出「这儿不对」。
     */
    private static String periodText(String limitType) {
        if (limitType == null || limitType.isBlank()) {
            return "不限";
        }
        return switch (limitType) {
            case TaskConst.LIMIT_DAILY -> "每日";
            case TaskConst.LIMIT_WEEKLY -> "每周";
            case TaskConst.LIMIT_ONCE -> "仅一次";
            case TaskConst.LIMIT_UNLIMITED -> "不限";
            default -> limitType;
        };
    }

    /** 档位的中间形态：阈值与奖励名在这里定下来，达没达标要等会员进度才知道 */
    private record Stage(int level, BigDecimal target, String rewardText) {
    }

    private static TaskCenterItem toItem(TaskConfig task, TaskRecord record, List<Stage> stages) {
        // 没有记录 = 还没开始做，进度是 0 而不是 null
        BigDecimal current = record == null ? BigDecimal.ZERO : record.getCurrentMetric();

        List<TaskStageView> stageViews = stages.stream()
                .sorted(Comparator.comparingInt(Stage::level))
                // 达没达标按「当前周期的进度 >= 本档阈值」判，和发奖那边同一个比较
                .map(stage -> new TaskStageView(stage.level(), stage.target(), stage.rewardText(),
                        stage.target() != null && current.compareTo(stage.target()) >= 0))
                .toList();

        return new TaskCenterItem(
                task.getId(),
                task.getTaskName(),
                task.getTaskGroup(),
                resolveTarget(task, stageViews),
                current,
                // 状态则保留 null：「还没开始」和「进行中 0 次」是两件事
                record == null ? null : record.getStatus(),
                stageViews,
                ruleText(task),
                periodText(task.getLimitType()),
                task.getStartTime(),
                task.getEndTime(),
                task.getActionUrl(),
                task.getSortWeight());
    }

    /**
     * 进度条的满格值：<b>最高档的阈值</b>。
     *
     * <p>没有配档位时才退回 {@code rule_config} 的目标 —— 那时也没有奖可发，
     * 进度条只是个进度条。
     *
     * <p>🔴 有档位却用 rule_config.targetCount 是错的：那两个是不同的源，
     * 而发奖判的是档位。运营把档位改成 1/3/7 却没动 targetCount=5，
     * 表现就是进度到 5 时进度条满了、第三档的奖却还没发。
     */
    private static BigDecimal resolveTarget(TaskConfig task, List<TaskStageView> stages) {
        return stages.stream()
                .map(TaskStageView::target)
                .filter(java.util.Objects::nonNull)
                .max(BigDecimal::compareTo)
                .orElseGet(() -> TaskRuleConfig.parse(task.getRuleConfig()).target());
    }
}
