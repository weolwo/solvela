package sa.base.module.support.job.core;

/**
 * 定时任务 执行接口。
 *
 * <p>实现类必须同时标注 {@link SmartJobHandler} 并注册为 Spring Bean，
 * 否则会在启动日志里被点名「将永远不会被调度」。
 *
 * <p>🔴 <b>原来的 {@code getClassName()} 默认方法已删除。</b> 它返回
 * {@code this.getClass().getName()}，而实现类只要挂了 {@code @Transactional} /
 * {@code @Async} 就会被 CGLIB 代理，类名变成 {@code Xxx$$SpringCGLIB$$0} ——
 * 与库里的配置对不上，任务静默不跑。匹配现在一律走 {@code @SmartJobHandler#name()}。
 *
 * <p><b>写执行器时的四条硬约束：</b>
 * <ol>
 *   <li>🔴 <b>响应中断。</b> 超时靠 {@code Future#cancel(true)}，本质是
 *       {@link Thread#interrupt()} —— 不响应中断的任务砍不掉，超时配了也白配。
 *       循环处理批量数据时，每批开头调一次 {@link SmartJobContext#checkCancelled()}；
 *       发起 HTTP / 远程调用时，自己带上连接与读取超时。</li>
 *   <li>🔴 <b>时间只用 {@code ctx}，不写 {@code LocalDateTime.now()}</b>（铁律 9/10）。
 *       {@link SmartJobContext#dbNow()} / {@link SmartJobContext#bizDate()} 都来自数据库时钟。
 *       散写 JVM 时间会引入第二个时钟源 —— 同一条铁律在 {@code TaskPeriodResolver}
 *       里已经立过一次，交接文档记着 497 行数据的实测代价。</li>
 *   <li>🔴 <b>事务方法不能被同类自调用</b>（铁律 11）。定时任务是这个坑的重灾区 ——
 *       job 入口天然没有事务上下文，{@code @TransactionalEventListener(AFTER_COMMIT)}
 *       在没有事务时<b>根本不投递</b>，表现是「记录都标成已处理了，下游一条没收到」。
 *       事务逻辑请拆到独立 Bean。</li>
 *   <li><b>幂等由你声明、由你保证。</b> 只有
 *       {@code @SmartJobHandler(idempotent = true)} 的执行器才允许配失败重试。
 *       不幂等却配了重试，等于把一次失败变成两次副作用。</li>
 *   <li><b>长任务要在循环里打 INFO 级进度日志。</b>
 *       后台的「查看实时日志」采集的是<b>你在执行线程上打的日志</b>（默认只收 INFO 及以上）。
 *       一个跑二十分钟却一行不打的执行器，卡住时那个面板上只有框架的一行「执行完毕」——
 *       等于没有。<b>「已处理 N/M 条」这种一行日志，是任务卡住时唯一的线索</b>；
 *       而返回值里的摘要只在<b>跑完之后</b>才有，救不了「正卡着」的场景。
 *       （2026-08-12 实测：内置任务因为只返回摘要不打日志，面板上只有 1 行框架输出。）</li>
 * </ol>
 *
 * @author huke
 * @date 2024/6/17 21:30
 */
public interface SmartJob {

    /**
     * 执行定时任务。
     *
     * @param ctx 执行上下文：参数、业务日期、数据库时钟、traceId、中断标记
     * @return 可为 null。自行组织人话描述执行结果，例如「本次处理数据 N 条」——
     *         这段文字会落进 {@code result_summary}，是运营唯一能看懂的东西。
     *         超长会被截断，别把明细往里塞
     * @throws Exception 抛出即视为执行失败，异常栈会被截断后落进 {@code error_detail}；
     *                   抛 {@link SmartJobCancelledException} 则落成「超时中断」而非「失败」
     */
    String execute(SmartJobContext ctx) throws Exception;
}
