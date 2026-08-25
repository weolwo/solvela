package solvela.admin.module.system.job.alarm;

/**
 * 定时任务告警通道。
 *
 * <p>做成接口而不是直接调消息模块，是因为<b>告警的落点因团队而异</b>：
 * 有的推站内信，有的推企微机器人，有的接自家监控。
 * 把通道抽出来，换落点时不用碰调度内核。
 *
 * <p>默认实现 {@link SolvelaJobLogAlarmSender} 只落 ERROR 日志 ——
 * 已经比「完全不知道」好，但仍需要有人去看日志，所以生产环境应当再挂一个真实通道。
 *
 * @author alaric
 * @date 2026-08-11
 */
public interface SolvelaJobAlarmSender {

    /**
     * 告警类型。分开是为了让接收方能按类型做不同处置 ——
     * {@link Type#SCHEDULER_DOWN} 是要半夜叫醒人的，其余两类可以等上班再看
     */
    enum Type {

        /**
         * 🔴 调度器可能已停摆：有任务超期未被触发。
         *
         * <p>这是三类里唯一能发现「任务根本没在跑」的信号 ——
         * 其余两类都建立在「任务确实跑了」的前提上
         */
        SCHEDULER_DOWN,

        /**
         * 任务连续失败达到阈值
         */
        JOB_CONTINUOUS_FAIL,

        /**
         * 执行器失联：库里配了但代码中不存在，该任务永远不会执行
         */
        HANDLER_MISSING,
    }

    /**
     * @param type     告警类型
     * @param title    一句话标题
     * @param content  明细
     * @param receiver 接收人（来自 {@code t_solvela_job.alarm_receiver}，可能为空）
     */
    void send(Type type, String title, String content, String receiver);
}
