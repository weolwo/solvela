package solvela.base.module.support.jobspi.core;

/**
 * 任务被中断（超时或节点停机）。
 *
 * <p>由 {@link SolvelaJobContext#checkCancelled()} 抛出。框架据此把执行记录落成
 * {@code TIMEOUT} 而不是 {@code FAIL} —— <b>这两者的处置方式完全不同</b>：
 * 超时说明任务本身没写错，是跑不完，该调参数或拆批次；失败才需要去查 bug。
 * 混在一起会让运营对着一堆「失败」束手无策。
 *
 * <p>刻意不继承业务异常体系：它不是业务错误，不该被全局异常处理器翻译成给用户看的提示。
 *
 * @author alaric
 * @date 2026-08-11
 */
public class SolvelaJobCancelledException extends RuntimeException {

    public SolvelaJobCancelledException(String message) {
        // 不需要堆栈：中断点在哪儿并不重要，重要的是「它被中断了」这个事实。
        // 定时任务可能在很深的循环里抛这个异常，采集堆栈纯属浪费
        super(message, null, false, false);
    }
}
