package solvela.task.taskevent.domain.command;

import solvela.enums.EnableStatusEnum;
import lombok.Data;

/**
 * 任务事件注册表 新增表单
 *
 * @Author alaric
 * @Date 2026-08-01
 */
@Data
public class TaskEventAddCommand {

    /**
     * 事件编码。
     *
     * <p>刻意约束成「大写字母 + 数字 + 下划线」：它是上游埋点要硬编码进代码里的字符串，
     * 允许小写或中划线只会制造 {@code order_paid} / {@code ORDER-PAID} 这类
     * 「看着一样、匹配不上」的事故 —— 而事件匹配不上时任务只是<b>安静地不动</b>，没有任何报错。
     */
    private String eventCode;

    /** 展示名 */
    private String eventName;

    /** 计量来源：NONE(计次) 或 payload 里的字段名(计额)，不填按 NONE */
    private String metricSource;

    /** 该事件会带哪些字段（JSON） */
    private String payloadSchema;

    /** 上游是否必须带幂等单号：1-必须, 0-可按事件日兜底 */
    private Integer bizIdRequired;

    /** 是否高频事件：1-是（预留，本期未实现路由优化） */
    private Integer isHighFrequency;

    /** 是否记录被丢弃事件的流水：1-记录, 0-不记录（高频事件建议关） */
    private Integer discardLogFlag;

    /** 备注：上游由谁埋点、什么时机触发 */
    private String remark;

    /** 状态：0-停用, 1-启用，不填按启用 */
    private EnableStatusEnum status;
}
