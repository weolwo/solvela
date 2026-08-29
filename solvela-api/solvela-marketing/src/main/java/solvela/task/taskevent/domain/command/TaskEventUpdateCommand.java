package solvela.task.taskevent.domain.command;

import solvela.enums.EnableStatusEnum;
import lombok.Data;

/**
 * 任务事件注册表 更新表单。
 *
 * <p>🔴 <b>刻意不定义 {@code eventCode}</b>，让「改编码」的意图在编译期就无处安放 ——
 * 与彩票的 {@code LotteryIssueUpdateCommand} 不定义 {@code issueNo} 是同一手法。
 * 事件编码是 {@code t_task_config.trigger_event} 的引用键，也是上游埋点里硬编码的字符串：
 * 改一次，所有已配好的任务当场收不到事件，而且<b>不会报任何错</b>，只是安静地不动。
 * 真要换编码，正确做法是新注册一个 + 把旧的停用。
 *
 * @Author alaric
 * @Date 2026-08-01
 */
@Data
public class TaskEventUpdateCommand {

    private Long id;

    /** 展示名 */
    private String eventName;

    /** 计量来源：NONE(计次) 或 payload 里的字段名(计额) */
    private String metricSource;

    /** 该事件会带哪些字段（JSON） */
    private String payloadSchema;

    /** 上游是否必须带幂等单号：1-必须, 0-可按事件日兜底 */
    private Integer bizIdRequired;

    /** 是否高频事件：1-是（预留，本期未实现路由优化） */
    private Integer isHighFrequency;

    /** 是否记录被丢弃事件的流水：1-记录, 0-不记录 */
    private Integer discardLogFlag;

    /** 备注 */
    private String remark;

    /** 状态：0-停用, 1-启用 */
    private EnableStatusEnum status;
}
