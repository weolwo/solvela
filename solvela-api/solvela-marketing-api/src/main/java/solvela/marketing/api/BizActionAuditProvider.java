package solvela.marketing.api;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 业务动作的<b>事后清单</b>：由动作的发生地实现，供任务域反查补推。
 *
 * <h3>为什么方向是"任务域来拉"，而不是"业务域去推"</h3>
 * 打点走的是发布/订阅（{@code BizActionEvent}），那条路覆盖不了一个窗口：
 * <b>业务事务提交了、进程在投递前挂了</b>。事件在内存里，进程没了就没了。
 *
 * <p>补这个窗口的第一反应是让业务域自己再推一次，但那条路走不通 ——
 * 业务域<b>没法知道自己漏了谁</b>：答案在 {@code t_task_record_flow} 里，
 * 而商城、外部场景一个字都不该认识任务域的表
 * （{@code PlayBoundaryTest} / {@code ExternalPlayBoundaryTest} 扫字节码守着）。
 *
 * <p>所以方向反过来：<b>任务域问"这些单我处理过没有"</b>，
 * 它自己知道答案，也能直接调引擎的入口补推。
 * 业务域只需要如实回答"窗口内有哪些单已经结清了"。
 *
 * <h3>为什么是 SPI 而不是 {@code @HttpExchange} 契约</h3>
 * 它是<b>内部运维</b>用的反查，不是 C 端链路的一环。今天四个域同进程，
 * 装配就是一个 {@code List} 注入；真拆服务时，拆出去的那个域自己带一个
 * HTTP 实现进来即可，本接口和调用方都不用改。
 *
 * <p>形状对齐 {@code ActivityRefProvider}：<b>List 注入 + 各实现自报 {@link #supportActionCode()}</b>，
 * 刻意不用 {@code Map<String, Provider>} 装配 —— 那个模式在本项目已经踩过三次
 * （GlobalEventDispatcher / PrizeStrategyFactory / AssetStrategyFactory），
 * 三次都是编译通过、运行恒 null 的静默失效。
 *
 * @author alaric
 * @date 2026-09-17
 */
public interface BizActionAuditProvider {

    /**
     * 本实现负责哪个业务动作（{@code solvela.event.BizActionCodes} 里的取值）。
     *
     * <p>一个动作只能有一个提供方 —— 谁产生这个动作，谁回答它。
     */
    String supportActionCode();

    /**
     * 窗口内<b>已经结清</b>的业务单。
     *
     * <h3>🔴 "结清"的判据必须和打点那一刻<b>完全一致</b></h3>
     * 商城的判据是订单已付（{@code PENDING} 及其之后的状态），
     * 外部场景是充值已成功。判据宽了会补推出一批本不该计数的单
     * （比如把"待支付"也算进来 —— 那是白送进度）；判据窄了则补不全，
     * 而补不全的表现和"没有这个任务"一模一样，不会有人发现。
     *
     * <p><b>所以实现时不要另写一套判断</b>，直接复用打点那一处的条件，
     * 最好是同一个常量、同一个 Dao 方法。
     *
     * @param from  窗口起（含）。按<b>结清时间</b>筛，不是创建时间
     * @param to    窗口止（含）。⚠️ 调用方会把它设在<b>若干分钟之前</b>：
     *              刚结清的那一单可能正在被线程池处理中，捞它等于和自己抢。
     *              这个延迟不是保守，是并发正确性
     * @param limit 单次最多返回多少条，防止一次把半年的单全拉进内存
     * @return 按结清时间正序；窗口内没有就返回空列表，<b>不要返回 null</b>
     */
    List<BizActionRecord> listSettled(LocalDateTime from, LocalDateTime to, int limit);
}
