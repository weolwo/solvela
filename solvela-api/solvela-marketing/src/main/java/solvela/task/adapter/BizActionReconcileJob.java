package solvela.task.adapter;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import solvela.base.module.jobspi.constant.SolvelaJobLaneEnum;
import solvela.base.module.jobspi.core.JobParam;
import solvela.base.module.jobspi.core.SolvelaJob;
import solvela.base.module.jobspi.core.SolvelaJobContext;
import solvela.base.module.jobspi.core.SolvelaJobHandler;
import solvela.enums.TaskConfigStatusEnum;
import solvela.marketing.api.BizActionAuditProvider;
import solvela.marketing.api.BizActionRecord;
import solvela.task.TaskConfig;
import solvela.task.TaskEvent;
import solvela.task.recordflow.dao.TaskRecordFlowDao;
import solvela.task.runtime.TaskEventService;
import solvela.task.runtime.domain.TaskEventReportCommand;
import solvela.task.taskconfig.dao.TaskConfigDao;
import solvela.task.taskevent.service.TaskEventDefService;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 打点对账：把<b>投递丢失</b>的业务动作捞回来补推。
 *
 * <h3>它补的是哪一个窗口</h3>
 * 打点走的是进程内的发布/订阅，覆盖不了一件事：
 * <b>业务事务提交了、进程在投递前挂了</b>。事件在内存里，进程没了就没了 ——
 * 订单已经付款、钱已经收了，却没有任何地方记得还欠着一次进度推进。
 *
 * <p>这个窗口<b>不是</b>靠 MQ 补的。引 MQ 也救不了它（publisher-confirm 只能告诉你
 * broker 收没收到），真正能覆盖的是 outbox，而 outbox 意味着一张表 + 一条写路径 +
 * 一个 relay。这里选了更省的一条：<b>反查</b>。
 *
 * <h3>为什么反查够用 —— 前提是"重推安全"，而它已经成立</h3>
 * {@code t_task_record_flow} 上有唯一键 {@code uk_t_tsk_flw_evt}
 * （task_config_id + member_id + event_biz_id），{@code TaskEventService} 在 catch
 * {@code DuplicateKeyException}。同一笔单无论被补推多少次，进度只会涨一次。
 *
 * <p>所以这里敢直接补推，不需要先加一张表。形状与
 * {@code PrizeDispatchReconcileJob} 一致 —— 那个 job 的注释里也写着同一句话：
 * 「重投是安全的」，因为下游按 {@code sourceBizId} 判重。
 *
 * <h3>为什么补推直接调 {@code TaskEventService.report}，不重发事件</h3>
 * 重发事件今天也能到（监听器开了 {@code fallbackExecution}，无事务时当场同步投递），
 * 但那会多绕一圈防腐层，而本 job 拿到的 {@link BizActionRecord} 已经是翻译好的形状。
 *
 * <p>更重要的是<b>职责</b>：补推是运维动作，它应当尽可能贴着引擎入口，
 * 少经过一层就少一层"正常链路改了、补推没跟上"的漂移面。
 *
 * <h3>只补"丢了会出事"的那一档</h3>
 * 订单支付、充值 —— 用户付了钱没进度是客诉。签到、浏览这类<b>刻意不补</b>：
 * 丢了就丢了，用户再点一次即可，为它们做对账是拿复杂度换一个没人抱怨的问题。
 * 哪些动作要补，由有没有人实现 {@link BizActionAuditProvider} 决定。
 *
 * @author alaric
 * @date 2026-09-17
 */
@Slf4j
@Component
@RequiredArgsConstructor
@SolvelaJobHandler(
        name = "bizActionReconcile",
        title = "【任务打点】漏投反查与补推",
        group = "BUSINESS",
        lane = SolvelaJobLaneEnum.SLOW,
        idempotent = true,
        defaultTimeoutSeconds = 600,
        params = {
                @JobParam(key = "delayMinutes", desc = "只处理多少分钟前结清的单，默认 10",
                        type = JobParam.Type.INT, defaultValue = "10"),
                @JobParam(key = "lookbackHours", desc = "往回看多少小时，默认 24",
                        type = JobParam.Type.INT, defaultValue = "24"),
                @JobParam(key = "limit", desc = "单个动作单次最多扫多少单，默认 500",
                        type = JobParam.Type.INT, defaultValue = "500"),
                @JobParam(key = "dryRun", desc = "试运行：只统计，不补推",
                        type = JobParam.Type.BOOLEAN, defaultValue = "false")
        }
)
public class BizActionReconcileJob implements SolvelaJob {

    /**
     * 各业务域提供的"事后清单"。
     *
     * <p>⚠️ 这个 List 在<b>只装了营销模块的进程里会是空的</b>（商城、外部场景的
     * 实现不在 classpath 上）。空就是空，不是错 —— 直接返回即可，
     * 别在这里抛异常或者告警：那会让一个合法的部署形态每分钟报一次错。
     *
     * <p>用 List 注入 + 各实现自报 {@code supportActionCode()}，而不是
     * {@code Map<String, Provider>} 装配 —— 后者在本项目已经踩过三次
     * （GlobalEventDispatcher / PrizeStrategyFactory / AssetStrategyFactory），
     * 三次都是编译通过、运行恒 null 的静默失效。形状对齐 {@code ActivityRefProvider}。
     */
    private final List<BizActionAuditProvider> auditProviders;

    private final TaskEventDefService taskEventDefService;
    private final TaskConfigDao taskConfigDao;
    private final TaskRecordFlowDao taskRecordFlowDao;
    private final TaskEventService taskEventService;

    @Override
    public String execute(SolvelaJobContext ctx) {
        if (auditProviders.isEmpty()) {
            return "本进程没有任何业务动作清单提供方，无需对账";
        }

        LocalDateTime now = ctx.dbNow();
        /*
         * ⚠️ 只看【足够旧】的单：刚结清的那一笔可能正在被 task-event-executor 处理中，
         *    捞它等于和自己抢 —— 补推会和正常链路撞在同一个唯一键上。
         *    撞了虽然不会算两次，但会在流水里留下一次毫无意义的重复尝试，
         *    也会让"补推了 N 笔"这个数字失去意义。
         *
         *    这个延迟不是保守，是并发正确性。原话在 PrizeDispatchReconcileJob 里。
         */
        LocalDateTime to = now.minusMinutes(ctx.intParam("delayMinutes", 10));
        LocalDateTime from = now.minusHours(ctx.intParam("lookbackHours", 24));
        int limit = ctx.intParam("limit", 500);
        boolean dryRun = ctx.boolParam("dryRun", false);

        StringBuilder summary = new StringBuilder();
        int totalMissing = 0;
        int totalRepushed = 0;

        for (BizActionAuditProvider provider : auditProviders) {
            ctx.checkCancelled();
            String actionCode = provider.supportActionCode();

            String skipReason = skipReason(actionCode, now);
            if (skipReason != null) {
                summary.append(String.format("[%s] 跳过：%s；", actionCode, skipReason));
                continue;
            }

            List<BizActionRecord> settled = provider.listSettled(from, to, limit);
            if (settled.isEmpty()) {
                continue;
            }

            List<BizActionRecord> missing = findMissing(actionCode, settled);
            totalMissing += missing.size();
            if (missing.isEmpty()) {
                continue;
            }

            if (dryRun) {
                summary.append(String.format("[%s] 待补推 %d 笔；", actionCode, missing.size()));
                continue;
            }
            int repushed = repushAll(actionCode, missing, ctx);
            totalRepushed += repushed;
            summary.append(String.format("[%s] 补推 %d/%d 笔；", actionCode, repushed, missing.size()));
        }

        String result = dryRun
                ? String.format("试运行：共 %d 笔漏投，本次未改动任何数据。%s", totalMissing, summary)
                : String.format("共 %d 笔漏投，补推成功 %d 笔。%s", totalMissing, totalRepushed, summary);
        if (totalMissing > 0) {
            /*
             * 🔴 漏投不为 0 就要 warn，别只 info。
             *
             * 进程正常运行时这个数应当长期是 0 —— 它一旦持续不为 0，说明的不是
             * "对账在干活"，而是【有什么东西在稳定地吃掉事件】：
             * 线程池长期打满、进程反复重启、或者有人在事务外发了 publish。
             * 对账把结果补上了，但根因还在，而且会以别的形式再冒出来。
             */
            log.warn("【任务打点对账】{}", result);
        } else {
            log.info("【任务打点对账】{}", result);
        }
        return result;
    }

    /**
     * 这个动作要不要对账。返回非 null 就是不用，内容是人话原因。
     *
     * <p>两道都必须有，而且顺序无所谓 —— 它们排除的是两种完全不同的"没人要"：
     * <ul>
     *   <li><b>事件没注册 / 已停用</b>：运营在后台关掉了它，正常链路此刻也不会推进度；</li>
     *   <li><b>没有任何任务订阅它</b>：事件是合法的，只是当前没人用。
     *       🔴 少了这一道会很难受：{@code t_task_record_flow} 里一条都没有，
     *       于是<b>窗口内每一单都会被判成"漏投"</b>，每轮都全量补推一遍 ——
     *       日志里天天报「补推 500 笔」，而实际什么都没发生。</li>
     * </ul>
     */
    private String skipReason(String actionCode, LocalDateTime now) {
        TaskEvent def = taskEventDefService.getEnabledByCode(actionCode);
        if (def == null) {
            return "事件未注册或已停用";
        }
        // 判据与 TaskEventService.findSubscribedConfigs 对齐：非下线、且在有效窗口内。
        // 两边写法不一致的话，会出现「运行态认为有人订阅、对账认为没人」这种对不上的状态
        Long subscribed = taskConfigDao.selectCount(new LambdaQueryWrapper<TaskConfig>()
                .eq(TaskConfig::getTriggerEvent, actionCode)
                .ne(TaskConfig::getStatus, TaskConfigStatusEnum.OFFLINE)
                .and(w -> w.isNull(TaskConfig::getStartTime).or().le(TaskConfig::getStartTime, now))
                .and(w -> w.isNull(TaskConfig::getEndTime).or().ge(TaskConfig::getEndTime, now)));
        if (subscribed == null || subscribed == 0) {
            return "当前没有任务订阅该事件";
        }
        return null;
    }

    /** 差集：清单里有、流水里没有的，就是投递路上丢掉的 */
    private List<BizActionRecord> findMissing(String actionCode, List<BizActionRecord> settled) {
        List<String> bizIds = settled.stream().map(BizActionRecord::bizId).toList();
        Set<String> reported = new HashSet<>(taskRecordFlowDao.selectReportedBizIds(actionCode, bizIds));
        return settled.stream().filter(r -> !reported.contains(r.bizId())).toList();
    }

    /**
     * 逐条补推。
     *
     * <p>一条失败不中断整批：补推本来就是尽力而为，
     * 为一条脏数据放弃剩下 499 条没有道理。失败的那条下一轮还会被扫出来。
     *
     * @return 实际推出去的笔数
     */
    private int repushAll(String actionCode, List<BizActionRecord> missing, SolvelaJobContext ctx) {
        int repushed = 0;
        for (BizActionRecord record : missing) {
            // 超时靠中断实现，每条开头自查一次（与 PrizeDispatchReconcileJob 同一做法）
            ctx.checkCancelled();
            try {
                TaskEventReportCommand cmd = new TaskEventReportCommand();
                cmd.setEventCode(actionCode);
                cmd.setMemberId(record.memberId());
                cmd.setEventBizId(record.bizId());
                /*
                 * 🔴 传【当初结清的时间】，不是现在。
                 *    传现在的话，昨晚 23:58 漏掉的那一单会被归进今天的周期 ——
                 *    用户昨天的进度凭空少一次、今天凭空多一次，而两边都说不清。
                 *    这也是 BizActionRecord 一定要带 occurredAt 的原因。
                 */
                cmd.setEventTime(record.occurredAt());
                cmd.setPayload(record.payload());
                /*
                 * isNewMember 不填：补推这条路走的是 TaskEventService 的公开入口，
                 * 拿不到防腐层那次会员查询。配了人群的任务会因此丢弃并写明原因，
                 * 这是【如实的】—— 比伪造一个 false 好，后者会变成一句看着正常的
                 * 「人群不匹配」。真要补全，该做的是把补推也走一遍防腐层的翻译，
                 * 而不是在这里猜。
                 */
                taskEventService.report(cmd);
                repushed++;
            } catch (RuntimeException e) {
                log.error("【任务打点对账】补推失败，下一轮会再试。action={}, bizId={}",
                        actionCode, record.bizId(), e);
            }
        }
        return repushed;
    }
}
