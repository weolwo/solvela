package solvela.admin.module.system.job.constant;

import lombok.AllArgsConstructor;
import lombok.Getter;
import solvela.enums.BaseEnum;

/**
 * 定时任务执行状态。
 *
 * <p>🔴 <b>取代原来的布尔 {@code success_flag}</b>（方案 §1.2 缺陷 7）。
 * 布尔表达不了「执行中」，所以原实现只能在执行<b>开始前</b>先写 {@code successFlag = true} ——
 * 进程一崩，库里就永久留着一条<b>假的「成功」记录</b>：
 * 运营看到的是绿的，而那次根本没跑完。这类记录还会让「上一次是否仍在执行」的判断永久误判。
 *
 * <p>本档先落地 {@link #RUNNING} / {@link #SUCCESS} / {@link #FAIL} / {@link #TIMEOUT}
 * 四态；{@link #PENDING} / {@link #BLOCKED} / {@link #MISFIRE} / {@link #INTERRUPTED}
 * 是抢占式调度（第二档）要用的，值先占好，避免二次改库。
 *
 * @author alaric
 * @date 2026-08-11
 */
@Getter
@AllArgsConstructor
public enum SolvelaJobExecuteStatusEnum implements BaseEnum {

    /**
     * 待执行。手动触发与失败重试都以这个状态入库，等扫描线程捞起（第二档）
     */
    PENDING(0, "待执行"),

    /**
     * 执行中。进程异常退出时会滞留在这个状态，由僵尸扫描回收（第二档）
     */
    RUNNING(1, "执行中"),

    SUCCESS(2, "成功"),

    FAIL(3, "失败"),

    /**
     * 超时被中断。⚠️ 与 FAIL 分开是有意义的：超时说明任务本身没写错，是跑不完，
     * 处理方式是调参数或拆批次，而不是查 bug
     */
    TIMEOUT(4, "超时中断"),

    /**
     * 上一次尚未结束，本次按阻塞策略被丢弃（第二档）
     */
    BLOCKED(5, "阻塞丢弃"),

    /**
     * 错过调度窗口（第二档）
     */
    MISFIRE(6, "错过调度"),

    /**
     * 节点停机或异常退出导致的中断（第二档）
     */
    INTERRUPTED(7, "中断"),

    ;

    /**
     * ⚠️ 字段必须叫 value（铁律 12）
     */
    private final Integer value;

    private final String desc;

    /**
     * 是否终态。非终态的记录才需要被僵尸扫描关注
     */
    public boolean isTerminal() {
        return this != PENDING && this != RUNNING;
    }
}
