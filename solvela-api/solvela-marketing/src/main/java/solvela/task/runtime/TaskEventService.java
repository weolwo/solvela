package solvela.task.runtime;

import solvela.enums.TaskFlowTypeEnum;
import solvela.enums.TaskConfigStatusEnum;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.extern.slf4j.Slf4j;
import solvela.base.json.JsonUtils;
import solvela.member.service.MemberService;
import solvela.task.constant.TaskConst;
import solvela.task.constant.TaskDiscardCode;
import solvela.task.record.dao.TaskRecordDao;
import solvela.task.recordflow.dao.TaskRecordFlowDao;
import solvela.task.TaskRecordFlow;
import solvela.task.runtime.domain.TaskAdvanceResult;
import solvela.task.runtime.domain.TaskEventContext;
import solvela.task.runtime.domain.TaskEventReportCommand;
import solvela.task.taskconfig.dao.TaskConfigDao;
import solvela.task.TaskConfig;
import solvela.task.TaskEvent;
import solvela.task.taskevent.service.TaskEventDefService;
import solvela.exception.BusinessException;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 任务事件入口与编排：<b>上游埋点唯一的进入口</b>。
 *
 * <p>职责只有三件：把事件规范化、找出订阅它的任务配置、把每个任务交给
 * {@link TaskRecordAdvanceService} 各自推进。<b>本类不写业务库</b>（除了被拒事件的留痕）。
 *
 * <p>🔴 <b>异步边界在这里，且刻意不用 {@code @Async} 注解</b>（方案 §4.7）：
 * 直接 submit 到自己的有界线程池，理由与 {@code GlobalEventDispatcher} 一致 ——
 * 注解式 @Async 走代理，一旦本类将来实现了某个 Spring 回调接口（如
 * {@code SmartInitializingSingleton}），JDK 动态代理会因为方法不在接口上而直接报
 * 「Need to invoke method ... but not found in any interface(s)」，
 * 那个坑该项目已经踩过一次并注释在案。手动 submit 没有这个隐患，行为也更直白。
 *
 * <p><b>异步的代价必须记住</b>：异常不再沿栈上抛（交接文档：「只能靠日志 + 下游表自查」）。
 * 所以每一条路径都必须在 {@code t_task_record_flow} 留痕 —— 那张表就是这条链路唯一的可观测面。
 *
 * @author alaric
 * @date 2026-08-01
 */
@Slf4j
@Service
public class TaskEventService {

    private final TaskConfigDao taskConfigDao;
    private final TaskRecordDao taskRecordDao;
    private final TaskRecordFlowDao taskRecordFlowDao;
    private final TaskRecordAdvanceService taskRecordAdvanceService;
    private final TaskEventDefService taskEventDefService;
    /**
     * 会员号 -> 账号。整条事件链只在 {@link #normalize} 里查这一次，
     * 之后靠 {@code TaskEventContext} 往下传 —— 事件是高频入口，不能每落一条流水就查一次库。
     */
    private final MemberService memberService;
    private final AsyncTaskExecutor taskEventExecutor;

    /**
     * 用 {@code @Qualifier} 而不是 {@code @Resource} 指定线程池：
     * {@code @Resource} 的 {@code @Target} 不含 {@code PARAMETER}，写在构造参数上编译期就报
     * 「注解接口不适用于该种类型的声明」。这里必须精确指定 —— 容器里有多个 AsyncTaskExecutor
     * （solvela-base 的 solvela-async-executor 与本模块的 task-event-executor），
     * 按类型注入会歧义，而<b>误注入到 solvela-async-executor 正是本类刻意要避开的那件事</b>
     * （与派奖链路共用队列，见 {@link TaskEventExecutorConfig}）。
     */
    public TaskEventService(TaskConfigDao taskConfigDao,
                            TaskRecordDao taskRecordDao,
                            TaskRecordFlowDao taskRecordFlowDao,
                            TaskRecordAdvanceService taskRecordAdvanceService,
                            TaskEventDefService taskEventDefService,
                            MemberService memberService,
                            @Qualifier(TaskEventExecutorConfig.TASK_EVENT_EXECUTOR)
                            AsyncTaskExecutor taskEventExecutor) {
        this.taskConfigDao = taskConfigDao;
        this.taskRecordDao = taskRecordDao;
        this.taskRecordFlowDao = taskRecordFlowDao;
        this.taskRecordAdvanceService = taskRecordAdvanceService;
        this.taskEventDefService = taskEventDefService;
        this.memberService = memberService;
        this.taskEventExecutor = taskEventExecutor;
    }


    /**
     * 并发冲突的重试次数（STREAK 乐观锁冲突 + 任务记录被并发创建，见 {@link TaskConcurrentModifyException}）。
     *
     * <p>刻意不做退避：冲突窗口只有「读 → 写」之间的几毫秒，退避只会拉长整体耗时。
     *
     * <p>次数取 5 是有依据的，不是拍脑袋：首次接取时 N 个并发事件里只有 1 个能建成记录，
     * 其余 N-1 个都要靠第 2 次尝试（那时记录已提交，直接命中 selectByUniqueKey）。
     * 也就是<b>绝大多数冲突两次就收敛</b>，留到 5 次是给 STREAK 的乐观锁连续冲突留余量。
     */
    private static final int MAX_RETRY_ATTEMPTS = 5;

    /**
     * 上游埋点入口：立即返回，真正的处理在任务事件线程池里跑。
     *
     * <p>返回失败只有一种情况：<b>队列打满被拒</b>。这是留给上游的重试信号 ——
     * 选了 AbortPolicy 就必须如实告诉上游「这条我没接住」，否则丢事件会变成静默的。
     */
    public void report(TaskEventReportCommand form) {
        // ① 事件必须已注册且启用 —— 注册表是「哪些事件合法」的唯一真源。
        //    不认识的事件当场拒绝，而不是丢进线程池里慢慢发现：上游拼错一个字母时，
        //    立刻收到 400 远比「返回 200 但任务永远不动」好排查。
        TaskEvent eventDef = taskEventDefService.getEnabledByCode(form.getEventCode());
        if (eventDef == null) {
            log.warn("[任务事件] 未注册或已停用的事件被拒。eventCode={}, memberId={}",
                    form.getEventCode(), form.getMemberId());
            throw new BusinessException("未注册或已停用的事件编码：" + form.getEventCode());
        }

        // ② 幂等键契约：t_task_event.biz_id_required 把「上游必须带单号」从口头约定变成强制校验。
        //    ⚠️ 这一步必须在 normalize 之前 —— normalize 会按事件日兜底填上 eventBizId，
        //    之后就再也分不清「上游传了」还是「服务端兜底的」。
        //    对订单类事件放过兜底 = 一天只算一笔，是资损级的错，不能只靠文档约定。
        if (Boolean.TRUE.equals(eventDef.getBizIdRequired()) && StringUtils.isBlank(form.getEventBizId())) {
            log.warn("[任务事件] 缺少必需的幂等单号被拒。eventCode={}, memberId={}",
                    form.getEventCode(), form.getMemberId());
            throw new BusinessException("事件 " + form.getEventCode() + " 必须携带 eventBizId（上游业务单号），否则无法防重");
        }

        TaskEventContext ctx = normalize(form, eventDef);
        try {
            taskEventExecutor.execute(() -> handleSafely(ctx));
        } catch (TaskRejectedException e) {
            log.error("[任务事件] 线程池队列已满，事件被拒。eventCode={}, memberId={}, eventBizId={}",
                    ctx.eventCode(), ctx.memberId(), ctx.eventBizId());
            saveRejectedFlow(ctx);
            throw new BusinessException("任务事件处理繁忙，请稍后重试");
        }
    }

    /**
     * 同步处理（供单测与联调脚本直接调用，避免为了断言去 sleep 等异步）。
     *
     * @return 每个匹配到的任务配置各一条结果
     */
    public List<TaskAdvanceResult> handle(TaskEventContext ctx) {
        // 这里再查一次注册表而不是复用 report() 查到的：handle 是对外公开的同步入口
        // （单测与联调脚本直接用它），两个入口各自自守，谁都不能靠另一个把关。
        TaskEvent eventDef = taskEventDefService.getEnabledByCode(ctx.eventCode());
        if (eventDef == null) {
            log.warn("[任务事件] 未注册或已停用的事件，整批丢弃。eventCode={}, member={}",
                    ctx.eventCode(), ctx.memberId());
            return List.of();
        }
        List<TaskConfig> configs = findSubscribedConfigs(ctx);
        if (configs.isEmpty()) {
            log.debug("[任务事件] 没有任务订阅该事件。eventCode={}, memberId={}", ctx.eventCode(), ctx.memberId());
            return List.of();
        }
        return configs.stream().map(config -> advanceWithRetry(config, ctx, eventDef)).toList();
    }

    /**
     * 线程池里的入口：任何异常都不能逃出去。
     *
     * <p>逃出去的后果不是崩溃而是<b>静默</b>：线程池的默认行为是把异常吞在 FutureTask 里，
     * 没有 {@code AsyncUncaughtExceptionHandler} 兜底（那只对注解式 @Async 生效）。
     */
    private void handleSafely(TaskEventContext ctx) {
        try {
            handle(ctx);
        } catch (RuntimeException e) {
            log.error("[任务事件] 处理异常。eventCode={}, member={}, eventBizId={}",
                    ctx.eventCode(), ctx.memberId(), ctx.eventBizId(), e);
        }
    }

    /**
     * 推进单个任务配置，并发冲突在这里重试。
     *
     * <p>🔴 <b>每次重试必须是一个全新事务，这正是 {@link TaskRecordAdvanceService} 拆成独立 Bean 的原因之一。</b>
     * 换新事务不只是「重来一次」，更是为了拿到<b>新的一致性视图</b> ——
     * MySQL 默认 REPEATABLE READ 下，同一事务里重查看不到别的事务后提交的行，
     * 就地重试无论多少次都是同一个结果。
     */
    private TaskAdvanceResult advanceWithRetry(TaskConfig config, TaskEventContext ctx, TaskEvent eventDef) {
        for (int attempt = 1; attempt <= MAX_RETRY_ATTEMPTS; attempt++) {
            try {
                return taskRecordAdvanceService.advance(config, ctx, eventDef);
            } catch (TaskConcurrentModifyException e) {
                log.info("[任务推进] 并发冲突，第 {}/{} 次重试。taskConfigId={}, member={}, 原因={}",
                        attempt, MAX_RETRY_ATTEMPTS, config.getId(), ctx.memberId(), e.getMessage());
            } catch (RuntimeException e) {
                // 单个任务配置出错不影响同一事件命中的其它任务 —— 这正是「每任务一个事务」的意义
                log.error("[任务推进] 失败。taskConfigId={}, member={}, eventBizId={}",
                        config.getId(), ctx.memberId(), ctx.eventBizId(), e);
                return new TaskAdvanceResult.Discarded("推进异常：" + e.getMessage());
            }
        }
        log.error("[任务推进] 并发冲突重试 {} 次仍失败。taskConfigId={}, member={}",
                MAX_RETRY_ATTEMPTS, config.getId(), ctx.memberId());
        return new TaskAdvanceResult.Discarded("并发冲突重试耗尽（CONCURRENT_RETRY_EXHAUSTED）");
    }

    /**
     * 找出订阅该事件的任务配置。
     *
     * <p>🔴 <b>判据是「status != 3 已下线」+ 时间窗，不是「status == 2 生效中」。</b>
     * 读码核实：全工程没有任何地方把 status 从 1 改成 2（{@code wizardSubmit} 落的就是 1，
     * 也不存在「启用」接口）。若判 {@code == 2}，所有任务永远不会被触发，
     * 而链路看起来完全正常 —— 事件收到了、日志也打了、就是一条进度都不涨。
     * 这正是铁律 16「前提不成立时通过和空过分不出来」的形状。
     *
     * <p>时间窗按<b>事件发生时间</b>判而不是 {@code now()}：迟到的事件应按它发生时的
     * 活动状态判定，否则「活动结束前 1 秒下的单，因为消息延迟没算进任务」会变成客诉。
     */
    private List<TaskConfig> findSubscribedConfigs(TaskEventContext ctx) {
        LambdaQueryWrapper<TaskConfig> query = new LambdaQueryWrapper<TaskConfig>()
                .eq(TaskConfig::getTriggerEvent, ctx.eventCode())
                .ne(TaskConfig::getStatus, TaskConfigStatusEnum.OFFLINE)
                .and(w -> w.isNull(TaskConfig::getStartTime).or().le(TaskConfig::getStartTime, ctx.eventTime()))
                .and(w -> w.isNull(TaskConfig::getEndTime).or().ge(TaskConfig::getEndTime, ctx.eventTime()));
        return taskConfigDao.selectList(query);
    }

    /**
     * 规范化：补齐幂等键与事件时间。
     *
     * <p>事件时间缺省取<b>数据库时钟</b>而不是 {@code LocalDateTime.now()}（铁律 9）——
     * 周期归属由它决定，多实例部署时各节点 JVM 时钟漂移会让同一秒的事件落到不同的天。
     */
    private TaskEventContext normalize(TaskEventReportCommand form, TaskEvent eventDef) {
        LocalDateTime eventTime = form.getEventTime() == null ? taskRecordDao.selectDbNow() : form.getEventTime();
        String eventBizId = TaskPeriodResolver.resolveEventBizId(form.getEventBizId(), eventTime);
        return new TaskEventContext(
                form.getEventCode(),
                form.getMemberId(),
                // 会员号必须真实存在；顺带取回账号，落进流水当展示快照。
                // 整条事件链只在这里查一次会员表，后面全靠 ctx 传。
                memberService.requireMemberName(form.getMemberId()),
                eventBizId,
                resolveAmount(form, eventDef),
                eventTime,
                form.getIsNewMember(),
                form.getPayload());
    }

    /**
     * 取计量值：调用方显式传的 {@code amount} 优先；没传则按注册表的 {@code metric_source}
     * 去 payload 里找。
     *
     * <p>这样上游可以只上报一份「订单支付」事件原文（含 payAmount），
     * 由注册表决定从哪个字段取金额 —— <b>换计量口径是改一行数据，不是改上游代码</b>，
     * 与「加事件只加一行数据」是同一个取向。
     */
    private BigDecimal resolveAmount(TaskEventReportCommand form, TaskEvent eventDef) {
        if (form.getAmount() != null) {
            return form.getAmount();
        }
        String metricSource = eventDef.getMetricSource();
        if (StringUtils.isBlank(metricSource)
                || TaskEventDefService.METRIC_SOURCE_NONE.equals(metricSource)
                || form.getPayload() == null) {
            return null;
        }
        Object raw = form.getPayload().get(metricSource);
        if (raw == null) {
            return null;
        }
        try {
            // 走 new BigDecimal(String) 而不是 valueOf(double)：后者会把 0.1 变成
            // 0.1000000000000000055511151231257827，一路带进金额累计
            return new BigDecimal(String.valueOf(raw).trim());
        } catch (NumberFormatException e) {
            log.warn("[任务事件] metric_source 字段不是数字，本次不计量。eventCode={}, field={}, value={}",
                    form.getEventCode(), metricSource, raw);
            return null;
        }
    }

    /**
     * 被线程池拒绝的事件也要留痕 —— 选了 AbortPolicy 就必须让丢弃可查。
     *
     * <p>此时还没匹配到任何任务配置，用 {@link TaskConst#FLOW_CONFIG_ID_NONE} 作哨兵。
     */
    private void saveRejectedFlow(TaskEventContext ctx) {
        try {
            TaskRecordFlow flow = new TaskRecordFlow();
            flow.setMemberId(ctx.memberId());
            flow.setMemberName(ctx.memberName());
            flow.setTaskConfigId(TaskConst.FLOW_CONFIG_ID_NONE);
            flow.setEventCode(ctx.eventCode());
            flow.setEventBizId(ctx.eventBizId());
            flow.setFlowType(TaskFlowTypeEnum.DISCARD);
            flow.setDiscardCode(TaskDiscardCode.POOL_REJECTED.getValue());
            flow.setDeltaMetric(BigDecimal.ZERO);
            flow.setAfterMetric(BigDecimal.ZERO);
            flow.setDiscardReason("任务事件线程池队列已满，事件被拒（拒绝策略 AbortPolicy），上游可重投");
            flow.setEventPayload(JsonUtils.toJson(ctx.payload()));
            taskRecordFlowDao.insert(flow);
        } catch (DuplicateKeyException ignored) {
            // 同一事件被拒多次，已记过一次
        } catch (RuntimeException e) {
            // 留痕本身失败时至少留日志 —— 兜底动作不能把自己炸掉
            log.error("[任务事件] 记录被拒流水失败。eventBizId={}", ctx.eventBizId(), e);
        }
    }
}
