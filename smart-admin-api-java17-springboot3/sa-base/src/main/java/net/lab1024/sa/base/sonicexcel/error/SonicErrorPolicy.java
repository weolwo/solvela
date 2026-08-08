package net.lab1024.sa.base.sonicexcel.error;

/**
 * 脏数据处置策略。
 *
 * <p><b>默认值刻意不对称</b>：导出默认 {@link FailFast}（报表少了几行等于事故），
 * 导入默认 {@link Collect}（用户就是会传脏数据，一行坏了不该毁掉整批）。
 *
 * <p>初版设计里"warn 日志 + 跳过该行"这个唯一策略是不能接受的：用户传 500 行、30 行被静默丢掉，
 * 界面只回一句"成功导入 470 条"，没人知道丢了谁。
 *
 * @Date 2026-08-08
 */
public sealed interface SonicErrorPolicy {

    /**
     * 遇错立即抛出，异常里带行号列名。
     */
    record FailFast() implements SonicErrorPolicy {
    }

    /**
     * 收集错误继续跑，累计超过 maxErrors 时熔断抛出。
     *
     * <p>熔断是必需的：千万行全脏时，一行一条日志能把磁盘写满。
     */
    record Collect(int maxErrors) implements SonicErrorPolicy {
    }

    /**
     * 静默跳过，结束时只打一条汇总日志（不是每行一条）。仅用于明知脏的离线任务。
     */
    record Skip() implements SonicErrorPolicy {
    }

    SonicErrorPolicy FAIL_FAST = new FailFast();

    static SonicErrorPolicy collect() {
        return new Collect(200);
    }
}
